#!/usr/bin/env python3
import argparse
import base64
import hashlib
import json
import os
import secrets
import string
import subprocess
import concurrent.futures
import shutil
import sys
import tempfile
from pathlib import Path

# 依赖：pip install qrcode[pil] pillow pyzbar reedsolo cryptography opencv-python moviepy | Dependencies: pip install qrcode[pil] pillow pyzbar reedsolo cryptography opencv-python moviepy
# 增强版：支持跨帧FEC（15-30%丢帧恢复）+ AES-256-GCM加密 | Enhanced version: supports cross-frame FEC (15-30% frame loss recovery) + AES-256-GCM encryption

try:
    from reedsolo import RSCodec
    from cryptography.hazmat.primitives.asymmetric import rsa, padding
    from cryptography.hazmat.primitives import serialization, hashes
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
    import qrcode
    from PIL import Image, ImageDraw
except Exception as e:
    print("[encode_qr_video] Missing dependencies:", e, file=sys.stderr)
    sys.exit(2)


def chunk_bytes(data: bytes, size: int):
    for i in range(0, len(data), size):
        yield data[i:i+size]


def make_qr(data: bytes, box_size=10, border=4):
    qr = qrcode.QRCode(
        version=None,
        error_correction=qrcode.constants.ERROR_CORRECT_Q,
        box_size=box_size,
        border=border,
    )
    qr.add_data(data)
    qr.make(fit=True)
    img = qr.make_image(fill_color="black", back_color="white").convert("RGB")
    return img


def generate_keypair():
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    public_key = private_key.public_key()
    pem_priv = private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption()
    )
    pem_pub = public_key.public_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PublicFormat.SubjectPublicKeyInfo
    )
    return pem_pub, pem_priv


def derive_key_from_passphrase(passphrase: str, salt: bytes) -> bytes:
    """使用PBKDF2从密码派生AES密钥 | Derive AES key from passphrase using PBKDF2"""
    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA256(),
        length=32,  # 256位密钥 | 256-bit key
        salt=salt,
        iterations=100000,  # 推荐的迭代次数 | Recommended iteration count
    )
    return kdf.derive(passphrase.encode('utf-8'))


def encrypt_payload_aes_gcm(data: bytes, passphrase: str, pubkey_pem: bytes) -> bytes:
    """使用AES-256-GCM加密载荷 | Encrypt payload using AES-256-GCM"""
    # 生成随机盐和nonce | Generate random salt and nonce
    salt = secrets.token_bytes(16)
    nonce = secrets.token_bytes(12)  # GCM模式推荐12字节nonce | GCM mode recommends 12-byte nonce
    
    # 从密码派生AES密钥 | Derive AES key from passphrase
    aes_key = derive_key_from_passphrase(passphrase, salt)
    
    # 使用AES-GCM加密 | Encrypt using AES-GCM
    aesgcm = AESGCM(aes_key)
    ciphertext = aesgcm.encrypt(nonce, data, None)
    
    # 用RSA公钥加密AES密钥 | Encrypt AES key with RSA public key
    public_key = serialization.load_pem_public_key(pubkey_pem)
    enc_key = public_key.encrypt(aes_key, padding.OAEP(
        mgf=padding.MGF1(algorithm=hashes.SHA256()), 
        algorithm=hashes.SHA256(), 
        label=None
    ))
    
    # 组装加密包：版本 + salt + nonce + 密钥长度 + 加密密钥 + 密文 | Assemble encrypted package: version + salt + nonce + key length + encrypted key + ciphertext
    header = b"AES256GCM"  # 9字节版本标识 | 9-byte version identifier
    return header + salt + nonce + len(enc_key).to_bytes(2, 'big') + enc_key + ciphertext


def create_cross_frame_fec(data_chunks: list[bytes], fec_ratio: float = 0.3) -> list[bytes]:
    chunk_count = len(data_chunks)
    parity_count = max(1, int(chunk_count * fec_ratio))
    rs = RSCodec(parity_count)
    max_chunk_size = max((len(c) for c in data_chunks), default=1024)
    padded_chunks = [c.ljust(max_chunk_size, b"\x00") for c in data_chunks]
    encoded_chunks: list[bytes] = [b"" for _ in range(chunk_count + parity_count)]
    for column in zip(*padded_chunks):
        encoded_position = rs.encode(bytes(column))
        base_len = len(padded_chunks)
        for j, b in enumerate(encoded_position):
            if j < base_len:
                encoded_chunks[j] += bytes([b])
            else:
                idx = chunk_count + (j - base_len)
                encoded_chunks[idx] += bytes([b])
    length_info = [len(c) for c in data_chunks]
    length_data = json.dumps(length_info).encode("utf-8")
    if len(encoded_chunks) > chunk_count:
        encoded_chunks[chunk_count] = length_data + b"|SPLIT|" + encoded_chunks[chunk_count]
    return encoded_chunks


def build_obfuscation_frame(width: int, height: int, seed: int | None) -> Image.Image:
    img = Image.new('RGB', (width, height), 'white')
    if seed is None:
        return img
    rnd = secrets.SystemRandom(seed)
    draw = ImageDraw.Draw(img)
    for _ in range(2000):
        x1 = rnd.randrange(0, width)
        y1 = rnd.randrange(0, height)
        x2 = min(width, x1 + rnd.randrange(5, 50))
        y2 = min(height, y1 + rnd.randrange(5, 50))
        color = tuple(rnd.randrange(0, 256) for _ in range(3))
        draw.rectangle([x1, y1, x2, y2], fill=color)
    return img


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--input', required=True)
    ap.add_argument('--output', required=True)
    ap.add_argument('--manifest', required=True)
    ap.add_argument('--grid', type=int, default=2)
    ap.add_argument('--fps', type=int, default=60)
    ap.add_argument('--resolution', default='1080p')
    ap.add_argument('--width', type=int)
    ap.add_argument('--height', type=int)
    ap.add_argument('--enable-fec', type=lambda x: x.lower() == 'true', default=True)
    ap.add_argument('--fec-ratio', type=float, default=0.3, help='FEC冗余比例(0.15-0.35) | FEC redundancy ratio (0.15-0.35)')
    ap.add_argument('--passphrase', required=True)
    ap.add_argument('--pubkey-hint', required=True)
    ap.add_argument('--privkey-frame', type=int, default=0)
    ap.add_argument('--privkey-frame-pass', required=True)
    ap.add_argument('--obfuscation')
    args = ap.parse_args()

    # 分辨率 | Resolution
    if args.width and args.height:
        W, H = args.width, args.height
    else:
        if args.resolution.lower() in ['1080p']:
            W, H = 1920, 1080
        elif args.resolution.lower() in ['720p']:
            W, H = 1280, 720
        elif args.resolution.lower() in ['4k', '2160p']:
            W, H = 3840, 2160
        else:
            W, H = 1920, 1080

    # 读取文件 | Read file
    data = Path(args.input).read_bytes()
    file_sha256 = hashlib.sha256(data).hexdigest()

    # 生成密钥对 | Generate key pair
    pub_pem, priv_pem = generate_keypair()

    # 载荷加密（使用AES-256-GCM） | Payload encryption (using AES-256-GCM)
    enc_payload = encrypt_payload_aes_gcm(data, args.passphrase, pub_pem)

    # 切片 | Slicing
    chunk_size = 800  # 减小块大小以适应QR码容量 | Reduce block size to fit QR code capacity
    chunks = list(chunk_bytes(enc_payload, chunk_size))

    # 跨帧FEC编码 | Cross-frame FEC encoding
    if args.enable_fec:
        fec_ratio = max(0.15, min(0.35, args.fec_ratio))  # 限制在15-35% | Limit to 15-35%
        fec_chunks = create_cross_frame_fec(chunks, fec_ratio)
    else:
        fec_chunks = chunks

    print(f"数据分块完成: 原始{len(chunks)}块 -> FEC后{len(fec_chunks)}块")

    # 生成二维码帧图 | Generate QR code frame images
    frames_dir = Path(tempfile.mkdtemp(prefix='qrframes_'))
    frames = []

    # 私钥帧：使用AES-GCM保护私钥 | Private key frame: protect private key using AES-GCM
    def protect_privkey_aes(priv_pem_bytes: bytes, pw: str) -> bytes:
        salt = secrets.token_bytes(16)
        nonce = secrets.token_bytes(12)
        key = derive_key_from_passphrase(pw, salt)
        aesgcm = AESGCM(key)
        ciphertext = aesgcm.encrypt(nonce, priv_pem_bytes, None)
        return b"PRIVKEY_AES" + salt + nonce + ciphertext

    protected_priv = protect_privkey_aes(priv_pem, args.privkey_frame_pass)
    priv_qr = make_qr(protected_priv, box_size=4, border=4)

    # 帧布局：n*n 子码，先简单堆叠 | Frame layout: n*n sub-codes, simple stacking first
    n = max(1, args.grid)
    cell_w = W // n
    cell_h = H // n

    def make_frame_with_codes(payload_blobs: list[bytes]) -> Image.Image:
        canvas = Image.new('RGB', (W, H), 'white')
        draw = ImageDraw.Draw(canvas)
        k = 0
        for i in range(n):
            for j in range(n):
                if k < len(payload_blobs):
                    qr_img = make_qr(payload_blobs[k], box_size=max(3, min(cell_w, cell_h)//50), border=2)
                    # 居中放入cell | Center in cell
                    x = j * cell_w + max(0, (cell_w - qr_img.width)//2)
                    y = i * cell_h + max(0, (cell_h - qr_img.height)//2)
                    canvas.paste(qr_img, (x, y))
                k += 1
        return canvas

    # 将数据块封装成带头的payload（增强版本标识） | Encapsulate data blocks into payload with header (enhanced version identifier)
    def wrap_chunk(idx: int, total: int, blob: bytes, is_fec: bool = False) -> bytes:
        # 使用新的头部格式以支持FEC标识 | Use new header format to support FEC identification
        head = b"QDV2" + idx.to_bytes(4,'big') + total.to_bytes(4,'big') + len(blob).to_bytes(2,'big')
        if is_fec:
            head += b"\x01"  # FEC标志 | FEC flag
        else:
            head += b"\x00"  # 普通数据标志 | Normal data flag
        crc = hashlib.crc32(blob).to_bytes(4, 'big')
        return head + blob + crc

    # 包装所有数据块 | Wrap all data blocks
    wrapped = []
    for i in range(len(fec_chunks)):
        is_fec_block = i >= len(chunks)  # 超出原始块数的为FEC冗余块 | Blocks beyond original count are FEC redundancy blocks
        wrapped.append(wrap_chunk(i, len(fec_chunks), fec_chunks[i], is_fec_block))

    # 构造帧序列：插入私钥帧与可选混淆帧 | Construct frame sequence: insert private key frame and optional obfuscation frame
    idx = 0
    batch = []
    while idx < len(wrapped):
        batch = wrapped[idx: idx + n*n]
        frame_img = make_frame_with_codes(batch)
        frames.append(frame_img)
        idx += n*n

    # 插入私钥帧和元数据帧 | Insert private key frame and metadata frame
    meta_frame = make_frame_with_codes([
        b"META2" + pub_pem,  # 公钥 | Public key
        b"FEC_INFO" + json.dumps({
            'original_chunks': len(chunks),
            'total_chunks': len(fec_chunks),
            'fec_ratio': args.fec_ratio if args.enable_fec else 0,
            'chunk_size': chunk_size
        }).encode('utf-8')
    ])
    
    priv_frame_index = max(0, min(len(frames), args.privkey_frame))
    frames.insert(priv_frame_index, meta_frame)
    frames.insert(priv_frame_index + 1, priv_qr)

    # 混淆帧 | Obfuscation frame
    if args.obfuscation:
        try:
            obf_bytes = Path(args.obfuscation).read_bytes()
            seed = int.from_bytes(hashlib.sha256(obf_bytes).digest()[:4], 'big')
            obf_frame = build_obfuscation_frame(W, H, seed)
            frames.insert(0, obf_frame)
            priv_frame_index += 1  # 调整私钥帧索引 | Adjust private key frame index
        except Exception:
            pass

    # 将帧写盘 | Write frames to disk
    tmp_out = frames_dir
    def _save(idx_img):
        i, im = idx_img
        im.save(tmp_out / f"{i:06d}.png")
    max_workers = max(1, min(8, (os.cpu_count() or 4)))
    with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as ex:
        list(ex.map(_save, enumerate(frames)))

    # 用ffmpeg合成视频 | Compose video using ffmpeg
    ffmpeg = os.environ.get('FFMPEG_CMD', 'ffmpeg')
    preset = os.environ.get('FFMPEG_PRESET', 'medium')
    crf = os.environ.get('FFMPEG_CRF', '20')
    cmd = [
        ffmpeg, '-y', '-r', str(args.fps), '-i', str(tmp_out / '%06d.png'),
        '-c:v', 'libx264', '-preset', preset, '-crf', crf, '-pix_fmt', 'yuv420p', args.output
    ]
    try:
        subprocess.run(cmd, check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    except subprocess.CalledProcessError as e:
        print(e.stdout.decode('utf-8', errors='ignore'))
        sys.exit(3)
    try:
        shutil.rmtree(tmp_out, ignore_errors=True)
    except Exception:
        pass

    # 生成清单（增强版） | Generate manifest (enhanced version)
    manifest = {
        'version': '2.0',  # 版本标识 | Version identifier
        'file_sha256': file_sha256,
        'frames': len(frames),
        'grid': n,
        'fps': args.fps,
        'resolution': {'w': W, 'h': H},
        'privkey_frame_index': priv_frame_index + 1,
        'pubkey_pem_b64': base64.b64encode(pub_pem).decode('ascii'),
        'encryption': 'AES-256-GCM',
        'fec_enabled': args.enable_fec,
        'fec_ratio': args.fec_ratio if args.enable_fec else 0,
        'original_chunks': len(chunks),
        'total_chunks': len(fec_chunks),
        'chunk_size': chunk_size,
        'obfuscation': bool(args.obfuscation),
    }
    Path(args.manifest).write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    
    print(json.dumps({
        'ok': True, 
        'video': args.output, 
        'manifest': args.manifest,
        'stats': {
            'original_size': len(data),
            'encrypted_size': len(enc_payload),
            'chunks': len(chunks),
            'fec_chunks': len(fec_chunks),
            'frames': len(frames),
            'fec_overhead': f"{(len(fec_chunks) - len(chunks)) / len(chunks) * 100:.1f}%" if chunks else "0%"
        }
    }))


if __name__ == '__main__':
    main()
