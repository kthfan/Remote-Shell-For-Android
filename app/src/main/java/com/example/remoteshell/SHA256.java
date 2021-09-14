package com.example.remoteshell;

/**
 * Modify from https://geraintluff.github.io/sha256/
 */
public class SHA256 {
    private static SHA256 _instance;
    public final long MAX_WORD = 4294967296l;
    public final int[] HASH = new int[64];
    public final int[] K = new int[64];

    public static SHA256 getInstance(){
        if (SHA256._instance == null)
        {
            SHA256._instance = new SHA256();
        }
        return SHA256._instance;
    }

    SHA256(){
        int primeCounter = 0;
        final short[] isComposite = new short[313];
        final int[] hash = this.HASH;
        final int[] k = this.K;
        final double maxWord = (double) this.MAX_WORD;

        for (short candidate = 2; primeCounter < 64; candidate++) {
            if (isComposite[candidate] == 0) {
                for (short i = 0; i < 313; i += candidate) {
                    isComposite[i] = candidate;
                }
                hash[primeCounter] = (int) (((long) Math.floor(Math.pow(candidate, 0.5) * maxWord)) & 0xffffffff);
                long n = ((long) Math.floor(Math.pow(candidate, 1./3.) * maxWord));
                if((n & 0b010000000000000000000000000000000L) == 0) n = n % MAX_WORD;
                else n = (n % MAX_WORD) - MAX_WORD;
                k[primeCounter++] = (int) n;
            }
        }
    }

    private int _rightRotate(int value, int amount){
        return (value>>>amount) | (value<<(32 - amount));
    }

    public byte[] encode(byte[] byteArr){
        int[] hash = this.HASH;
        final int[] k = this.K;
        final double maxWord = (double) this.MAX_WORD;

        int[] words;
        int byteArrLength;
        int byteArrBitLength = byteArr.length*8;
        byte[] result = new byte[32];

        int _remainder = (byteArr.length + 1) % 64;
        if(_remainder > 56) _remainder = 64 - _remainder + 56;
        else _remainder = 56 - _remainder;
        byte[] _tempByteArr = new byte[byteArr.length + 1 + _remainder];
        int _offset = 0;
        for(int i=0; i<byteArr.length; i++, _offset++){
            _tempByteArr[_offset] = byteArr[i];
        }
        _tempByteArr[_offset++] = (byte) 0x80;
        for(int i=0; i<_remainder; i++, _offset++){
            _tempByteArr[_offset] = 0;
        }
        byteArr = _tempByteArr;

        byteArrLength = byteArr.length;
        final int _preWordLength = (byteArrLength>>2)+2;
        words = new int[_preWordLength];
        for (int i = 0; i < byteArrLength; i++) {
            int j = (int) byteArr[i] & 0xff;
            words[i>>2] |= j << ((3 - i)%4)*8;
        }
        words[(byteArrLength>>2)] = (int) Math.floor((byteArrBitLength/maxWord));
        words[(byteArrLength>>2)+1] = (byteArrBitLength);

        // process each chunk
        for (int j = 0; j < words.length;) {
            int[] w = new int[64];  // The message is expanded into 64 words as part of the iteration
            for(int i=0; i<16; i++){
                w[i] = words[i+j];
            }
            j += 16;

            int[] oldHash = hash;
            // This is now the undefinedworking hash", often labelled as variables a...g
            // (we have to truncate as well, otherwise extra entries at the end accumulate
            int[] _arr = new int[72];
            for(int i=64; i<72; i++)
                _arr[i] = hash[i-64];
            hash = _arr;

            for (int i = 0; i < 64; i++) {
                int i2 = i + j;
                int iHash = 64 - i;
                // Expand the message into 64 words
                // Used below if

                // Iterate
                int a = hash[0+iHash], e = hash[4+iHash];
                int _num1 = (this._rightRotate(e, 6) ^ this._rightRotate(e, 11) ^ this._rightRotate(e, 25)); // S1
                int _num2 = ((e&hash[5+iHash])^((~e)&hash[6+iHash])); // ch

                if(i >= 16){ // Expand the message schedule if needed
                    int w15 = w[i - 15], w2 = w[i - 2];
                    int _num11 = (this._rightRotate(w15, 7) ^ this._rightRotate(w15, 18) ^ (w15>>>3)); // s0
                    int _num12 =  (this._rightRotate(w2, 17) ^ this._rightRotate(w2, 19) ^ (w2>>>10)); // s1
                    w[i] = (w[i - 16] + _num11 + w[i - 7] + _num12) | 0;
                }
                int temp1 = hash[7+iHash] + _num1 + _num2 + k[i] + w[i];


                // This is only used once, so *could* be moved below, but it only saves 4 bytes and makes things unreadble
                int _num3 = (this._rightRotate(a, 2) ^ this._rightRotate(a, 13) ^ this._rightRotate(a, 22)); // S0
                int _num4 =  ((a&hash[1+iHash])^(a&hash[2+iHash])^(hash[1+iHash]&hash[2+iHash])); // maj
                int temp2 = _num3 + _num4;

                // We don't bother trimming off the extra ones, they're harmless as long as we're truncating when we do the slice()

                hash[iHash-1] = (temp1 + temp2) | 0;

                iHash--;
                hash[4+iHash] = (hash[4+iHash] + temp1)|0;
            }

            for (int i = 0; i < 8; i++) {
                hash[i] = (hash[i] + oldHash[i])|0;
            }
        }
        int offset = 0;
        for (int i = 0; i < 8; i++) {
            for (int j = 3; j + 1 != 0 ; j--) {
                byte b = (byte) ((hash[i]>>(j*8))&255);
                result[offset++] = b;
            }
        }
        return result;
    }
}

