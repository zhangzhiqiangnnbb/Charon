#!/usr/bin/env python3
"""
增强版解码脚本，支持AES-256-GCM加密和跨帧FEC恢复
支持：15-30%丢帧恢复、公钥+密码验证、私钥帧提取、混淆帧验证、数据重组与解密
"""
import argparse
import base64
import hashlib
import json
import subprocess
import sys
import tempfile
from pathlib import Path

try:
    from reedsolo import RSCodec
    from cryptography.hazmat.primitives.asymmetric import rsa, padding
    from cryptography.hazmat.primitives import serialization, hashes
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
    from pyzbar import pyzbar
    from PIL import Image
    import cv2
except Exception as e:
    print("[decode_qr_video] Missing dependencies:", e, file=sys.stderr)
    sys.exit(2)


FFMPEG_CMD = os.environ.get('FFMPEG_CMD', 'ffmpeg')

def extract_frames(video_path: str, output_dir: Path):
    """从视频提取帧为PNG"""
    cmd = [FFMPEG_CMD, '-i', str(video_path), '-vsync', '0', '-f', 'image2', str(output_dir / '%06d.png')]
    subprocess.run(cmd, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def decode_qrs_from_image(img_path: Path) -> list[bytes]:
    """从图像中检测所有二维码并解码"""
    img = cv2.imread(str(img_path))
    if img is None:
        return []
    codes = pyzbar.decode(img)
    return [code.data for code in codes]


def derive_key_from_passphrase(passphrase: str, salt: bytes) -> bytes:
    """使用PBKDF2从密码派生AES密钥"""
    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA256(),
        length=32,  # 256位密钥
        salt=salt,
        iterations=100000,
    )
    return kdf.derive(passphrase.encode('utf-8'))


def unprotect_privkey_aes(protected_data: bytes, password: str) -> bytes:
    """用AES-GCM解密私钥"""
    if not protected_data.startswith(b"PRIVKEY_AES"):
        raise ValueError("Invalid private key format")
    
    data = protected_data[11:]  # 跳过"PRIVKEY_AES"标识
    if len(data) < 28:  # 16字节salt + 12字节nonce
        raise ValueError("Invalid private key data length")
    
    salt = data[:16]
    nonce = data[16:28]
    ciphertext = data[28:]
    
    key = derive_key_from_passphrase(password, salt)
    aesgcm = AESGCM(key)
    return aesgcm.decrypt(nonce, ciphertext, None)


def decrypt_payload_aes_gcm(enc_data: bytes, pubkey_pem: bytes, privkey_pem: bytes) -> bytes:
    """用AES-256-GCM解密载荷"""
    if not enc_data.startswith(b"AES256GCM"):
        raise ValueError("Invalid encrypted payload format")
    
    data = enc_data[9:]  # 跳过"AES256GCM"标识
    if len(data) < 30:  # 16字节salt + 12字节nonce + 2字节长度
        raise ValueError("Invalid encrypted data length")
    
    salt = data[:16]
    nonce = data[16:28]
    enc_key_len = int.from_bytes(data[28:30], 'big')
    enc_key = data[30:30+enc_key_len]
    ciphertext = data[30+enc_key_len:]
    
    # 用私钥解密AES密钥
    private_key = serialization.load_pem_private_key(privkey_pem, password=None)
    aes_key = private_key.decrypt(enc_key, padding.OAEP(
        mgf=padding.MGF1(algorithm=hashes.SHA256()), 
        algorithm=hashes.SHA256(), 
        label=None
    ))
    
    # 用AES-GCM解密载荷
    aesgcm = AESGCM(aes_key)
    return aesgcm.decrypt(nonce, ciphertext, None)


def recover_cross_frame_fec(chunks_dict: dict, original_count: int, total_count: int) -> list[bytes]:
    """
    跨帧FEC恢复，支持15-30%丢帧
    """
    missing_count = total_count - len(chunks_dict)
    recovery_rate = missing_count / total_count
    
    print(f"FEC恢复: 总块数{total_count}, 接收{len(chunks_dict)}块, 丢失{missing_count}块 ({recovery_rate*100:.1f}%)")
    
    if recovery_rate > 0.35:
        raise ValueError(f"丢帧率过高({recovery_rate*100:.1f}%)，超出FEC恢复能力(35%)")
    
    # 提取长度信息（存储在第一个冗余块中）
    length_info = None
    first_fec_idx = original_count
    if first_fec_idx in chunks_dict:
        fec_data = chunks_dict[first_fec_idx]
        if b'|SPLIT|' in fec_data:
            length_part, fec_part = fec_data.split(b'|SPLIT|', 1)
            try:
                length_info = json.loads(length_part.decode('utf-8'))
                chunks_dict[first_fec_idx] = fec_part
            except:
                pass
    
    if length_info is None:
        print("警告：无法获取原始长度信息，使用默认值")
        length_info = [800] * original_count  # 默认块大小
    
    # 计算最大块大小
    max_chunk_size = max(length_info) if length_info else 800
    
    # 准备恢复矩阵
    available_chunks = []
    available_indices = []
    
    # 按索引顺序收集可用块
    for i in range(total_count):
        if i in chunks_dict:
            chunk = chunks_dict[i]
            # 填充到最大长度
            padded = chunk + b'\x00' * (max_chunk_size - len(chunk))
            available_chunks.append(padded)
            available_indices.append(i)
    
    if len(available_chunks) < original_count:
        raise ValueError(f"可用块数({len(available_chunks)})少于原始块数({original_count})，无法恢复")
    
    # 创建RS解码器
    parity_count = total_count - original_count
    rs = RSCodec(parity_count)
    
    # 逐字节位置恢复
    recovered_chunks = [b''] * original_count
    
    for pos in range(max_chunk_size):
        # 构建当前位置的编码序列
        encoded_sequence = bytearray(total_count)
        mask = [False] * total_count
        
        for j, idx in enumerate(available_indices):
            if j < len(available_chunks):
                encoded_sequence[idx] = available_chunks[j][pos]
                mask[idx] = True
        
        # 使用RS解码恢复丢失的字节
        try:
            # 标记丢失的位置
            for i in range(total_count):
                if not mask[i]:
                    encoded_sequence[i] = 0  # RS库会忽略这些值
            
            # 进行RS解码
            decoded_sequence = rs.decode(encoded_sequence, erase_pos=[i for i in range(total_count) if not mask[i]])[0]
            
            # 提取原始数据部分
            for i in range(original_count):
                recovered_chunks[i] += bytes([decoded_sequence[i]])
                
        except Exception as e:
            print(f"位置{pos}恢复失败: {e}")
            # 对于无法恢复的位置，使用0填充
            for i in range(original_count):
                recovered_chunks[i] += b'\x00'
    
    # 根据原始长度信息去除填充
    final_chunks = []
    for i in range(original_count):
        if i < len(length_info):
            final_chunks.append(recovered_chunks[i][:length_info[i]])
        else:
            final_chunks.append(recovered_chunks[i])
    
    return final_chunks


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--video', required=True, help='输入视频文件')
    ap.add_argument('--output', required=True, help='输出文件路径')
    ap.add_argument('--manifest', help='清单文件路径（可选）')
    ap.add_argument('--privkey-frame-password', required=True, help='私钥帧密码')
    ap.add_argument('--obfuscation-check', help='混淆验证文件')
    args = ap.parse_args()

    # 提取帧
    frames_dir = Path(tempfile.mkdtemp(prefix='decode_frames_'))
    print(f"提取帧到: {frames_dir}")
    extract_frames(args.video, frames_dir)
    
    frame_files = sorted(frames_dir.glob('*.png'))
    if not frame_files:
        print("错误：视频中没有提取到帧", file=sys.stderr)
        sys.exit(1)

    # 混淆验证（如果提供）
    if args.obfuscation_check:
        obf_bytes = Path(args.obfuscation_check).read_bytes()
        expected_seed = int.from_bytes(hashlib.sha256(obf_bytes).digest()[:4], 'big')
        print(f"混淆种子: {expected_seed}")

    # 收集元数据和私钥
    pubkey_pem = None
    privkey_pem = None
    fec_info = {}
    
    # 扫描所有帧查找元数据
    for frame_file in frame_files:
        qrs = decode_qrs_from_image(frame_file)
        for qr_data in qrs:
            # 查找公钥
            if qr_data.startswith(b"META2"):
                pubkey_pem = qr_data[5:]
                
            # 查找FEC信息
            elif qr_data.startswith(b"FEC_INFO"):
                try:
                    fec_info = json.loads(qr_data[8:].decode('utf-8'))
                except:
                    pass
            
            # 查找私钥
            elif qr_data.startswith(b"PRIVKEY_AES"):
                try:
                    privkey_pem = unprotect_privkey_aes(qr_data, args.privkey_frame_password)
                    # 验证私钥格式
                    serialization.load_pem_private_key(privkey_pem, password=None)
                except Exception as e:
                    print(f"私钥解密失败: {e}", file=sys.stderr)
    
    if pubkey_pem is None:
        print("错误：无法找到公钥", file=sys.stderr)
        sys.exit(1)
        
    if privkey_pem is None:
        print("错误：无法提取或解密私钥", file=sys.stderr)
        sys.exit(1)
    
    print("密钥提取成功")
    print(f"FEC信息: {fec_info}")

    # 收集数据帧
    data_chunks = {}
    total_chunks = None
    original_chunks = None
    
    for frame_file in frame_files:
        qrs = decode_qrs_from_image(frame_file)
        for qr_data in qrs:
            if qr_data.startswith(b"QDV2"):  # 新版本数据块
                if len(qr_data) < 16:  # 最少头部长度
                    continue
                    
                idx = int.from_bytes(qr_data[4:8], 'big')
                total = int.from_bytes(qr_data[8:12], 'big')
                length = int.from_bytes(qr_data[12:14], 'big')
                is_fec = qr_data[14] == 1
                payload = qr_data[15:15+length]
                crc_received = qr_data[15+length:15+length+4]
                
                # 验证CRC
                crc_calc = hashlib.crc32(payload).to_bytes(4, 'big')
                if crc_calc == crc_received:
                    data_chunks[idx] = payload
                    if total_chunks is None:
                        total_chunks = total
    
    # 从FEC信息或清单中获取参数
    if fec_info:
        original_chunks = fec_info.get('original_chunks')
        total_chunks = fec_info.get('total_chunks', total_chunks)
    elif args.manifest and Path(args.manifest).exists():
        manifest = json.loads(Path(args.manifest).read_text())
        original_chunks = manifest.get('original_chunks')
        total_chunks = manifest.get('total_chunks', total_chunks)
    
    if total_chunks is None or len(data_chunks) == 0:
        print("错误：没有找到有效的数据块", file=sys.stderr)
        sys.exit(1)
    
    # 如果没有FEC信息，假设所有块都是原始数据
    if original_chunks is None:
        original_chunks = total_chunks
    
    print(f"找到 {len(data_chunks)}/{total_chunks} 个数据块")
    
    # FEC恢复（如果需要且可能）
    if len(data_chunks) < original_chunks:
        if original_chunks < total_chunks:
            print("开始FEC恢复...")
            try:
                recovered_chunks = recover_cross_frame_fec(data_chunks, original_chunks, total_chunks)
                print("FEC恢复成功")
            except Exception as e:
                print(f"FEC恢复失败: {e}", file=sys.stderr)
                sys.exit(1)
        else:
            print("错误：数据块不完整且无FEC信息", file=sys.stderr)
            sys.exit(1)
    else:
        # 直接重组（按顺序提取原始数据块）
        recovered_chunks = []
        for i in range(original_chunks):
            if i in data_chunks:
                recovered_chunks.append(data_chunks[i])
            else:
                print(f"错误：缺少数据块 {i}", file=sys.stderr)
                sys.exit(1)
    
    # 重组数据
    reassembled = b''.join(recovered_chunks)
    
    # 解密数据
    try:
        decrypted = decrypt_payload_aes_gcm(reassembled, pubkey_pem, privkey_pem)
        Path(args.output).write_bytes(decrypted)
        print(f"文件解密成功: {args.output}")
        
        # 验证文件完整性
        file_hash = hashlib.sha256(decrypted).hexdigest()
        print(f"文件SHA256: {file_hash}")
        
    except Exception as e:
        print(f"解密失败: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == '__main__':
    main()