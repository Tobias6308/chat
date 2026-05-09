import CryptoJS from 'crypto-js';

export function encryptMD5(str: string): string {
  return CryptoJS.MD5(str).toString();
}