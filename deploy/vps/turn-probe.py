#!/usr/bin/env python3
"""Authenticate to TURN and transfer a payload through two real relay allocations.

On VPS: python3 turn-probe.py --env-file .env
On another network: python turn-probe.py --server 103.77.172.232 --password
Password input is hidden and never accepted as a command-line argument.
Uses only Python's standard library; never executes .env as code.
"""
import argparse
import getpass
import hashlib
import hmac
import ipaddress
import os
from pathlib import Path
import re
import socket
import struct
import sys

COOKIE = 0x2112A442


def attribute(kind, data):
    return struct.pack('!HH', kind, len(data)) + data + b'\0' * (-len(data) % 4)


def parse(packet):
    if len(packet) < 20:
        raise RuntimeError('Truncated STUN header')
    kind, length, cookie, transaction = struct.unpack('!HHI12s', packet[:20])
    if cookie != COOKIE or len(packet) != length + 20:
        raise RuntimeError('Invalid STUN packet')
    attrs, offset = {}, 20
    while offset < len(packet):
        if offset + 4 > len(packet):
            raise RuntimeError('Truncated STUN attribute')
        key, size = struct.unpack('!HH', packet[offset:offset + 4])
        if offset + 4 + size > len(packet):
            raise RuntimeError('Truncated STUN attribute data')
        attrs[key] = packet[offset + 4:offset + 4 + size]
        offset += 4 + size + (-size % 4)
    return kind, transaction, attrs


def xor_address(address):
    host, port = address
    packed = int(ipaddress.IPv4Address(host)) ^ COOKIE
    return struct.pack('!BBHI', 0, 1, port ^ (COOKIE >> 16), packed)


def decode_address(value):
    if len(value) != 8 or value[1] != 1:
        raise RuntimeError('Expected an IPv4 relay allocation')
    _, _, port, address = struct.unpack('!BBHI', value)
    return str(ipaddress.IPv4Address(address ^ COOKIE)), port ^ (COOKIE >> 16)


class TurnClient:
    def __init__(self, server, port, transport, username, password, timeout):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM if transport == 'tcp' else socket.SOCK_DGRAM)
        self.sock.settimeout(timeout)
        self.sock.connect((server, port))
        self.tcp = transport == 'tcp'
        self.username = username
        self.password = password
        self.realm = self.nonce = None
        self.allocated = False
        self.challenge_seen = False
        self.last_packet = None

    def exact(self, size):
        data = b''
        while len(data) < size:
            chunk = self.sock.recv(size - len(data))
            if not chunk:
                raise RuntimeError('TURN server closed the TCP connection')
            data += chunk
        return data

    def receive(self):
        if not self.tcp:
            packet = self.sock.recv(65535)
        else:
            header = self.exact(20)
            length = struct.unpack('!H', header[2:4])[0]
            packet = header + self.exact(length)
        self.last_packet = packet
        return parse(packet)

    def auth_key(self):
        return hashlib.md5(self.username.encode() + b':' + self.realm + b':' + self.password.encode()).digest()

    def verify_integrity(self, packet):
        offset = 20
        while offset + 4 <= len(packet):
            kind, size = struct.unpack('!HH', packet[offset:offset + 4])
            if kind == 0x0008:
                if size != 20:
                    break
                # RFC 5389: header length ends at MESSAGE-INTEGRITY, while the
                # HMAC input ends immediately before that attribute itself.
                signed = packet[:2] + struct.pack('!H', offset + 4) + packet[4:offset]
                expected = hmac.new(self.auth_key(), signed, hashlib.sha1).digest()
                if hmac.compare_digest(expected, packet[offset + 4:offset + 24]):
                    return
                raise RuntimeError('TURN response MESSAGE-INTEGRITY verification failed')
            offset += 4 + size + (-size % 4)
        raise RuntimeError('TURN success response is missing MESSAGE-INTEGRITY')

    def packet(self, kind, attrs, transaction, authenticate=True):
        body = b''.join(attribute(key, value) for key, value in attrs)
        if authenticate and self.realm is not None:
            body += attribute(0x0006, self.username.encode())
            body += attribute(0x0014, self.realm)
            body += attribute(0x0015, self.nonce)
            key = self.auth_key()
            header = struct.pack('!HHI12s', kind, len(body) + 24, COOKIE, transaction)
            signature = hmac.new(key, header + body, hashlib.sha1).digest()
            return header + body + attribute(0x0008, signature)
        return struct.pack('!HHI12s', kind, len(body), COOKIE, transaction) + body

    def request(self, kind, attrs):
        for _ in range(3):
            transaction = os.urandom(12)
            packet = self.packet(kind, attrs, transaction)
            # UDP retransmission is bounded and uses the same transaction ID.
            for retry in range(2 if not self.tcp else 1):
                self.sock.sendall(packet)
                try:
                    while True:
                        response, response_id, result = self.receive()
                        if response_id == transaction:
                            break
                    break
                except socket.timeout:
                    if self.tcp or retry == 1:
                        raise
            if response == (kind | 0x0100):
                if not self.challenge_seen or self.realm is None:
                    raise RuntimeError('TURN accepted an unauthenticated request; authentication is not configured')
                self.verify_integrity(self.last_packet)
                return result
            raw_error = result.get(0x0009, b'\0\0\0\0')
            error = (raw_error[2] & 7) * 100 + raw_error[3]
            if error in (401, 438) and 0x0014 in result and 0x0015 in result:
                if not result[0x0014] or not result[0x0015]:
                    raise RuntimeError('TURN authentication challenge has an empty realm/nonce')
                if self.realm is None and error != 401:
                    raise RuntimeError('Expected initial TURN 401 authentication challenge')
                self.challenge_seen = True
                self.realm, self.nonce = result[0x0014], result[0x0015]
                continue
            raise RuntimeError('TURN request failed with code ' + str(error))
        raise RuntimeError('TURN authentication failed; check credentials/realm')

    def allocate(self):
        result = self.request(0x0003, [(0x0019, b'\x11\0\0\0')])
        if 0x0016 not in result:
            raise RuntimeError('Allocation succeeded without XOR-RELAYED-ADDRESS')
        self.allocated = True
        return decode_address(result[0x0016])

    def permit(self, peer):
        self.request(0x0008, [(0x0012, xor_address(peer))])

    def send(self, peer, payload):
        self.sock.sendall(self.packet(0x0016, [(0x0012, xor_address(peer)), (0x0013, payload)],
                                     os.urandom(12), authenticate=False))

    def receive_payload(self, expected):
        kind, _, attrs = self.receive()
        if kind != 0x0017 or attrs.get(0x0013) != expected:
            raise RuntimeError('Expected TURN DATA indication with the test payload')

    def close(self):
        try:
            if self.allocated:
                self.request(0x0004, [(0x000D, struct.pack('!I', 0))])
        except (OSError, RuntimeError):
            pass
        finally:
            self.sock.close()


def read_env(path):
    result = {}
    for line in Path(path).read_text(encoding='utf-8-sig').splitlines():
        match = re.fullmatch(r'\s*(PUBLIC_IP|TURN_SECRET)\s*=(.*)', line)
        if not match:
            continue
        key, value = match.groups()
        if key in result:
            raise RuntimeError('Duplicate ' + key + ' in .env')
        value = value.strip()
        if len(value) > 1 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        result[key] = value
    return result


def probe(server, port, transport, username, password, timeout):
    clients = []
    try:
        for _ in range(2):
            clients.append(TurnClient(server, port, transport, username, password, timeout))
        first, second = clients
        one, two = first.allocate(), second.allocate()
        if not all(49152 <= port <= 49200 for _, port in (one, two)):
            raise RuntimeError('Relay port is outside configured range 49152:49200')
        first.permit(two)
        second.permit(one)
        payload = b'telehealth-turn-' + os.urandom(16)
        first.send(two, payload)
        second.receive_payload(payload)
        second.send(one, payload)
        first.receive_payload(payload)
        print('PASS ' + transport.upper() + ': authenticated allocations; bidirectional relay payload; '
              + one[0] + ':' + str(one[1]) + ' / ' + two[0] + ':' + str(two[1]))
    finally:
        for client in clients:
            client.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', help='TURN IPv4 address or hostname')
    parser.add_argument('--port', type=int, default=3478)
    parser.add_argument('--username', default='telehealth')
    parser.add_argument('--env-file', default='.env')
    parser.add_argument('--password', action='store_true', help='Prompt for password instead of reading .env')
    parser.add_argument('--transport', choices=['udp', 'tcp', 'both'], default='both')
    parser.add_argument('--timeout', type=float, default=5)
    args = parser.parse_args()
    try:
        values = {} if args.password else read_env(args.env_file)
        server = args.server or values.get('PUBLIC_IP')
        password = getpass.getpass('TURN password (hidden): ') if args.password else values.get('TURN_SECRET')
        if not server or not password:
            raise RuntimeError('Set PUBLIC_IP/TURN_SECRET in .env or use --server HOST --password')
        transports = ['udp', 'tcp'] if args.transport == 'both' else [args.transport]
        for transport in transports:
            probe(server, args.port, transport, args.username, password, args.timeout)
        print('The test covers this client network only. Also test the browser call across Wi-Fi and mobile data.')
        return 0
    except (OSError, RuntimeError, ValueError) as error:
        # Do not display .env, STUN authentication attributes, or credentials.
        print('FAIL TURN: ' + str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
