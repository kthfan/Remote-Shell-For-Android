package com.example.remoteshell;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;

@RequiresApi(api = Build.VERSION_CODES.N)
public class RSA {

    public final static BigInteger[] FITST_PRIMES_LIST =
            (BigInteger[]) Arrays.asList(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97, 101, 103, 107, 109, 113, 127, 131, 137, 139, 149, 151, 157, 163, 167, 173, 179, 181, 191, 193, 197, 199, 211, 223, 227, 229, 233, 239, 241, 251, 257, 263, 269, 271, 277, 281, 283, 293, 307, 311, 313, 317, 331, 337, 347, 349, 353, 359, 367, 373, 379, 383, 389, 397, 401, 409, 419, 421, 431, 433, 439, 443, 449, 457, 461, 463, 467, 479, 487, 491, 499, 503, 509, 521, 523, 541, 547, 557, 563, 569, 571, 577, 587, 593, 599, 601, 607, 613, 617, 619, 631, 641, 643, 647, 653, 659, 661, 673, 677, 683, 691, 701, 709, 719, 727, 733, 739, 743, 751, 757, 761, 769, 773, 787, 797, 809, 811, 821, 823, 827, 829, 839, 853, 857, 859, 863, 877, 881, 883, 887, 907, 911, 919, 929, 937, 941, 947, 953, 967, 971, 977, 983, 991, 997)
                    .stream()
                    .map(e -> BigInteger.valueOf(e))
                    .toArray(size -> new BigInteger[size]);
    public final static int PADDING_K0 = 32;
    public final static int PADDING_K1 = 8;
    public final static int PADDING_N = 64;
    private final static SecureRandom crypto = new SecureRandom();

    private BigInteger[] _privateKey = null;
    private BigInteger[] _publicKey = null;

    public byte[] getPublicKey(){
        if(this._publicKey == null) return null;
        return this._serializeKey(this._publicKey);
    }
    public byte[] getPrivateKey(){
        if(this._privateKey == null) return null;
        BigInteger p = this._privateKey[2], q = this._privateKey[3], d = this._privateKey[0];
        return this._serializeKey(new BigInteger[]{p, q, d});
    }
    public void setPublicKey(byte[] pubkey){
        this._publicKey = this._deserializeKey(pubkey);
    }
    public void setPrivateKey(byte[] prikey){
        BigInteger p, q, d, dmp1, dmq1, coeff;
        BigInteger[] deserialized = this._deserializeKey(prikey);
        p = deserialized[0]; q = deserialized[1]; d = deserialized[2];
        BigInteger[] factors = this._factorPrivate(p, q, d);
        dmp1 = factors[0]; dmq1 = factors[1]; coeff = factors[2];
        BigInteger n = p.multiply(q);
        this._privateKey = new BigInteger[]{d, n, p, q, dmp1, dmq1, coeff};
    }

    public RSA generateKeyPair() throws Exception {
        return this.generateKeyPair(2024);
    }
    public RSA generateKeyPair(int _bits) throws Exception {
        return this.generateKeyPair(_bits, false);
    }
    public RSA generateKeyPair(boolean checkCorrect) throws Exception {
        return this.generateKeyPair(2024, false);
    }
    public RSA generateKeyPair(int _bits, boolean checkCorrect) throws Exception {
        BigInteger bits = BigInteger.valueOf(_bits);
        BigInteger p, q, n,
                lambda_n, e, k, d, r,
                dmp1, dmq1, coeff;
        BigInteger[] returnedList;

        returnedList = this._getTwoPrimes(_bits);
        p = returnedList[0]; q = returnedList[1];
        n = p.multiply(q);
        lambda_n = RSA.lcm(p.subtract(BigInteger.ONE), q.subtract(BigInteger.ONE));
        e = this._getE(lambda_n, p, q);
        returnedList = this._extendedEuclidean(lambda_n, e);
        k = returnedList[0]; d = returnedList[1]; r = returnedList[2];
        // if(r !== 1n) return generateKeyPair(bits); //throw 'extendedEuclidean fall.';
        if(!this._isKeyPairSafe(p, q, n, e, d, bits)) return this.generateKeyPair(_bits, checkCorrect); //throw 'the keypair are not safe.';
        returnedList = this._factorPrivate(p, q, d);

        dmp1 = returnedList[0]; dmq1 = returnedList[1]; coeff = returnedList[2];

        this._publicKey = new BigInteger[]{e, n};
        this._privateKey = new BigInteger[]{d, n, p, q, dmp1, dmq1, coeff};
        if(checkCorrect && !this.checkKeyPairCorrectness()) return this.generateKeyPair(_bits, checkCorrect);

        return this;
    }

    public byte[] encrypt(byte[] M){
//        var [e, n] = this._publicKey;
        return this._encrypt(M, false, this._publicKey);
    }
    public byte[] decrypt(byte[] C){
        return this._decrypt(C, true, this._privateKey);
    }

    public byte[] sign(byte[] M){
        M = SHA256.getInstance().encode(M);
        return this._encrypt(M, true, this._privateKey);
    }
    public boolean verify(byte[] S, byte[] M){
        byte[] C = this._decrypt(S, false, this._publicKey);
        M = SHA256.getInstance().encode(M);
        for(int i=0; i<M.length; i++){
            if(M[i] != C[i]) return false;
        }
        return true;
    }

    public boolean checkKeyPairCorrectness(){
        return this.checkKeyPairCorrectness(1);
    }
    public boolean checkKeyPairCorrectness(int iter){
        byte[] data, encrypted, decrypted, signData;
        BigInteger randStart = BigInteger.ONE.shiftLeft(511);
        BigInteger randEnd = BigInteger.ONE.shiftLeft(512);
        for(int i=0; i<iter; i++){
            data = this._bint2arr(RSA.randint(randStart, randEnd));
            signData = this._bint2arr(RSA.randint(randStart, randEnd));
            encrypted = this.encrypt(data);
            decrypted = this.decrypt(encrypted);
            for(int j=0; j<data.length; j++){
                if(data[j] != decrypted[j]) return false;
            }
            if(!(this.verify(this.sign(data), data) && !this.verify(this.sign(signData), data))) return false;
        }
        return true;
    }

    private byte[] _encrypt(byte[] M, boolean privateMod, BigInteger[] key){
        byte[][] C;
        BigInteger n = key[1];
        // find log256(n)


        int ln2 = n.bitLength();
        int ln256 = ln2 / 8;
        if((ln2 & 0b111) != 0) ln256++;

        M = this._pkcs7pad(M);
        M = this._paddingSplit(M);

        byte[][] MM = this._splitByN(M, n);

        int _len;

        _len = MM.length; C = new byte[_len][];
        for(int i=0; i<_len; i++){
            BigInteger m, c;
            m = this._arr2bint(MM[i]);
            c = privateMod ? this._chineseRemainder(m, key[2], key[3], key[4], key[5], key[6]) : RSA.modExp(m, key[0], key[1]);
            C[i] = this._bint2arr(c, ln256); // chunk size is ln256
        }

        return this._flatArray(C);
    }
    private byte[] _decrypt(byte[] C, boolean privateMod, BigInteger[] key){
        byte[][] MM;
        byte[] M;
        BigInteger n = key[1];
        int chunkSize;
        int _len;
        // find log256(n)
        int ln2 = n.bitLength();
        int ln256 = ln2 >> 3;
        if((ln2 & 0b111) != 0) ln256++;
        chunkSize = ln256;

        byte[][] CC = this._splitByN(C, n, chunkSize);

        _len = CC.length; MM = new byte[_len][];
        for(int i=0; i<_len; i++){
            BigInteger m, c;
            c = this._arr2bint(CC[i]);
            m = privateMod ? this._chineseRemainder(c, key[2], key[3], key[4], key[5], key[6]) : RSA.modExp(c, key[0], key[1]);
            MM[i] = this._bint2arr(m);
        }

        M = this._flatArray(MM);
        M = this._unpaddingSplit(M);
        M = this._pkcs7strip(M);
        return M;
    }

    private BigInteger[] _factorPrivate(BigInteger p, BigInteger q, BigInteger d){
        BigInteger k, coeff, r, dmp1, dmq1;
        BigInteger[] returnedList = this._extendedEuclidean(p, q);
        k = returnedList[0]; coeff = returnedList[1]; r = returnedList[2];
        dmp1 = d.remainder(p.subtract(BigInteger.ONE)); dmq1 = d.remainder(q.subtract(BigInteger.ONE));
        return new BigInteger[]{dmp1, dmq1, coeff};
    }

    /** modify from https://github.com/travist/jsencrypt*/
    private BigInteger _chineseRemainder(BigInteger x, BigInteger p, BigInteger q, BigInteger dmp1, BigInteger dmq1, BigInteger coeff) {
        //  re-calculate any missing CRT params
        BigInteger xp = RSA.modExp(x.remainder(p), dmp1, p);
        BigInteger xq = RSA.modExp(x.remainder(q), dmq1, q);

        while (xp.compareTo(xq) < 0) {
            xp = xp.add(p);
        }
        return xp.subtract(xq).multiply(coeff).remainder(p).multiply(q).add(xq);
    }

    private byte[][] _splitByN(byte[] arr, BigInteger n){
        return this._splitByN(arr, n, -1);
    }
    private byte[][] _splitByN(byte[] arr, BigInteger n, int chunkSize){ // 0 <= m < n
        int x = n.bitLength() - 1;

        if(n.remainder(BigInteger.ONE.shiftLeft(x)).compareTo(BigInteger.ZERO) == 0) x++;
        if(chunkSize == -1) chunkSize =  x >> 3;//must less than n, hence can not + 1;
        int arrLength = arr.length;
        int len = arrLength / chunkSize;
        if(arrLength % chunkSize != 0) len++;
        byte[][] result = new byte[len][];
        for(int i=0; i<len; i++){
            if((i + 1)*chunkSize > arr.length) result[i] = Arrays.copyOfRange(arr, i*chunkSize, arr.length);
            else result[i] = Arrays.copyOfRange(arr, i*chunkSize, (i + 1)*chunkSize);
        }
        return result;
    }

    private byte[] _serializeKey(BigInteger[] k){// k is array of bigint
        final int chunkSize = RSA.PADDING_N;

        int nKeys = k.length;
        int[] resLenArr = new int[nKeys];
        int totalLen = 0;
        byte[][] _arr = new byte[nKeys][];
        for(int i=0; i<nKeys; i++){
            byte[] _tmp;
            _tmp = this._bint2arr(k[i]);
            _tmp = this._pkcs7pad(_tmp);
            _tmp = this._paddingSplit(_tmp);
            _arr[i] = _tmp;
            totalLen += _tmp.length;
            resLenArr[i] = _tmp.length / chunkSize;
        }

        totalLen = totalLen + nKeys;
        byte[] resArr = new byte[totalLen]; //[e.length, e.arr, n.arr]

        int offset = 0;
        resArr[offset++] = (byte) nKeys;// set number of components
        for(int j=0; j<nKeys-1; j++){// set length of key components
            resArr[offset++] = (byte) resLenArr[j];
        }
        for(int j=0; j<nKeys; j++){// set values to resArr
            byte[] _tmp = _arr[j];
            for(int _i = 0; _i < _tmp.length; _i++){
                resArr[offset++] = _tmp[_i];
            }
        }
        return resArr;
    }
    private BigInteger[] _deserializeKey(byte[] k){
        final int chunkSize = RSA.PADDING_N;

        int offset = 0;
        int nKeys = k[offset++] & 0xff; // byte to integer
        short[] lenArr = new short[nKeys];
        int totalLen;
        int _tmp = 0;
        for(int i=0; i< nKeys-1; i++){
            lenArr[i] = (short) (k[offset++] & 0xff);
            _tmp += lenArr[i];
        }
        totalLen = k.length - offset;
        lenArr[nKeys-1] = (short) (totalLen - _tmp);
        BigInteger[] resArr = new BigInteger[nKeys];
        for(int i=0; i< nKeys; i++){
            byte[] _k;
            int size = lenArr[i] * chunkSize;
            if(offset + size > k.length) _k = Arrays.copyOfRange(k, offset, k.length);
            else _k = Arrays.copyOfRange(k, offset, offset + size);
            _k = this._unpaddingSplit(_k);
            _k = this._pkcs7strip(_k);
            resArr[i] = this._arr2bint(_k);

            offset += size;
        }
        return resArr;
    }

    private byte[] _paddingSplit(byte[] message){//split array to 24 length, because some array may to long.
        final int n = RSA.PADDING_N, k0 = RSA.PADDING_K0, k1 = RSA.PADDING_K1;
        final int inc = n - k0 - k1;

        int iter = (int) message.length / inc;
        if(message.length % inc != 0) iter++;
        byte[][] result = new byte[iter][];
        for(int i=0,offset=0; i<iter; i++, offset += inc){
            byte[] m = Arrays.copyOfRange(message, offset, offset+inc);
//            if(m.length <= inc){
//                m = this._paddingZeros(m, inc - m.length);
//            }
            result[i] = this._padding(m);
        }
        return this._flatArray(result);
    }
    private byte[] _unpaddingSplit(byte[] message){//
        final int chunkSize = RSA.PADDING_N;
        if(message.length % chunkSize != 0) message = this._paddingZeros(message, chunkSize - (message.length % chunkSize) ); //padding zeros

        int iter = message.length / chunkSize;
        byte[][] result = new byte[iter][];
        for(int i=0,offset=0; i<iter; i++, offset += chunkSize){
            byte[] R = Arrays.copyOfRange(message, offset, offset+chunkSize);
            result[i] = this._unpadding(R);
        }
        return this._flatArray(result);
    }

    private byte[] _pkcs7pad(byte[] data) {
        return this._pkcs7pad(data, RSA.PADDING_N-RSA.PADDING_K0-RSA.PADDING_K1);
    }
    private byte[] _pkcs7pad(byte[] data, int n) {
        int padder = n - (data.length % n);
        byte[] result = new byte[data.length + padder];
        System.arraycopy(data, 0, result, 0, data.length);
        for (int i = data.length; i < result.length; i++) {
            result[i] = (byte) padder;
        }
        return result;
    }
    private byte[] _pkcs7strip(byte[] data) {
        int padder = data[data.length - 1] & 0xff;
        int length = data.length - padder;
        return Arrays.copyOfRange(data, 0, length);
    }
    private byte[] _padding(byte[] m){ //required length: n-k0-k1 = 24, output length: n = 64
        final int n = RSA.PADDING_N, k0 = RSA.PADDING_K0, k1 = RSA.PADDING_K1;
//        const [G, H] = [RSA.HASH_FUNCTION_G, RSA.HASH_FUNCTION_H];

        m = this._paddingZeros(m, k1); // padding k1 zeros

        byte[] r = new byte[k0];    RSA.crypto.nextBytes(r);
        byte[] X = this._xorArray(m,  SHA256.getInstance().encode(r)); //assert X.length === n - k0;
        byte[] Y = this._xorArray(r, SHA256.getInstance().encode(X));

        return this._concatArray(X, Y);
    }
    private byte[] _unpadding(byte[] R){//output length: 24
        final int n = RSA.PADDING_N, k0 = RSA.PADDING_K0, k1 = RSA.PADDING_K1;
//        const [G, H] = [RSA.HASH_FUNCTION_G, RSA.HASH_FUNCTION_H];

//        var [X, Y] = [R.slice(0, n - k0), R.slice(n - k0, n)];
        byte[] X = Arrays.copyOfRange(R, 0, n - k0);
        byte[] Y = Arrays.copyOfRange(R, n - k0, n);
        byte[] r = this._xorArray(Y, SHA256.getInstance().encode(X));
        byte[] mZeros = this._xorArray(X, SHA256.getInstance().encode(r));
        return Arrays.copyOfRange(mZeros, 0, n - k0 - k1);
    }
    private byte[] _xorArray(byte[] a, byte[] b){
        byte[] result = new byte[a.length];
        for(int i=0; i<a.length; i++){
            result[i] = (byte) (a[i] ^ b[i]);
        }
        return result;
    }
    private byte[] _paddingZeros(byte[] arr, int numOfZeros){
        int len = arr.length + numOfZeros;
        byte[] result = new byte[len];
        for(int i=0; i<arr.length; i++) result[i] = arr[i];
        for(int i=arr.length; i<len; i++) result[i] = 0;
        return result;
    }
    private byte[] _flatArray(byte[][] arr){ // two dim array required
        int len = 0;
        for(int i=0; i<arr.length; i++){
            len += arr[i].length;
        }
        byte[] result = new byte[len];
        int offset = 0;
        for(int i=0; i<arr.length; i++){
            for(int j=0; j<arr[i].length; j++)
                result[offset++] = arr[i][j];
        }
        return result;
    }
    private byte[] _concatArray(byte[] A, byte[] B){
        int al = A.length, len = al + B.length;
        byte[] result = new byte[len];
        for(int i=0; i<al; i++) result[i] = A[i];
        for(int i=0, j=al; j<len; i++, j++) result[j] = B[i];
        return result;
    }

    private BigInteger _arr2bint(byte[] arr){
        int len = arr.length;
        BigInteger bint = BigInteger.ZERO;
        for(int i=0; i<len; i++){
            bint = bint.add(BigInteger.valueOf(arr[i] & 0xff).shiftLeft(BigInteger.valueOf(i).shiftLeft(3).intValue()));
        }
        return bint;
    }
    private byte[] _bint2arr(BigInteger bint){
        int ln2 = bint.bitLength();
        int len = ln2 / 8;
        if((ln2 & 0b111) != 0) len++;
        return this._bint2arr(bint, len);
    }
    private byte[] _bint2arr(BigInteger bint, int len){
        byte[] buffer = new byte[len];
        for(int i=0; i<len; i++) {
            buffer[i] = bint.remainder(BigInteger.valueOf(256)).byteValue();
            bint = bint.shiftRight(8);
        }
        return buffer;
    }

    private boolean _isKeyPairSafe(BigInteger p, BigInteger q, BigInteger n, BigInteger e, BigInteger d, BigInteger bits){
        if(p.compareTo(q.shiftLeft(1)) < 0 && p.compareTo(q) > 0 && d.compareTo(BigInteger.ONE.shiftLeft(bits.divide(BigInteger.valueOf(4)).intValue()).divide(BigInteger.valueOf(3))) < 0)
            return false;
        return true;
    }

    private BigInteger[] _getTwoPrimes(int bits) throws Exception {
        /** p - q should larger than 2n^{1/4}*/
        int pBits = bits >> 1;
        BigInteger[] range = new BigInteger[]{
                (BigInteger.ONE.shiftLeft(pBits-1)).add(BigInteger.ONE),
                (BigInteger.ONE.shiftLeft(pBits)).subtract(BigInteger.ONE)
        };
        BigInteger step = range[1].subtract(range[0]);
        BigInteger dist = BigInteger.ONE.shiftLeft((bits>>2) + 2);
        return new BigInteger[]{this._generatePrimeNumberByProbability(range[1].add(dist), range[1].add(dist).add(step)), this._generatePrimeNumberByProbability(range[0], range[1])};
    }

    /** modify from https://github.com/travist/jsencrypt*/
    private BigInteger _getLowLevelPrime(BigInteger n0, BigInteger n1){
        final BigInteger[] LOW_PRIME_LIST = RSA.FITST_PRIMES_LIST;
        final int LOW_PRIME_LENGTH = LOW_PRIME_LIST.length;
        final BigInteger BIG_LOW_PRIME = RSA.FITST_PRIMES_LIST[LOW_PRIME_LENGTH - 1];
        final BigInteger lplim = BigInteger.ONE.shiftLeft(26).divide(BIG_LOW_PRIME).add(BigInteger.ONE);

        while(true){
            // Obtain a random number
            BigInteger x = RSA.randint(n0, n1);
            if(x.add(BigInteger.ONE).compareTo(BigInteger.ZERO) == 0) x = x.add(BigInteger.ONE);

            if (x.compareTo(BigInteger.ONE.shiftLeft(28)) < 0 && x.compareTo(BIG_LOW_PRIME) <= 0) { // check if x is prime that in list "LOW_PRIME_LIST"
                for (int i = 1; i < LOW_PRIME_LENGTH; i++) {// not including 2
                    if (x.compareTo(LOW_PRIME_LIST[i]) == 0) {
                        return x;
                    }
                }
                continue;
            }

            int i = 1;
            boolean _notPrime = false;
            while (i < LOW_PRIME_LENGTH) {
                BigInteger m = LOW_PRIME_LIST[i];
                int j = i + 1;
                while (j < LOW_PRIME_LENGTH && m.compareTo(lplim) < 0) {
                    m = m.multiply(LOW_PRIME_LIST[j++]);
                }
                m = x.remainder(m);
                while (i < j) {
                    if (m.remainder(LOW_PRIME_LIST[i++]).compareTo(BigInteger.ZERO) == 0) {
                        _notPrime = true;
                        break;
                    }
                }
                if(_notPrime) break;
            }
            if(_notPrime) continue;
            return x;
        }
    }
    /** modify from https://github.com/travist/jsencrypt*/
    private boolean _MillerRabinPrimalityTest(BigInteger n) {
        final BigInteger[] LOW_PRIME_LIST = RSA.FITST_PRIMES_LIST;
        final int LOW_PRIME_LENGTH = LOW_PRIME_LIST.length;
        final BigInteger n1 = n.subtract(BigInteger.ONE);
        int t = 10;

        int k = 0;
        while(true){
            if(n1.and(BigInteger.ONE.shiftLeft(k)).compareTo(BigInteger.ZERO) != 0) break;
            k++;
        }

        if (k <= 0) {
            return false;
        }
        final BigInteger r = n1.shiftRight(k);
        t = (t + 1) >> 1;
        if (t > LOW_PRIME_LENGTH) {
            t = LOW_PRIME_LENGTH;
        }
        int count = RSA.randint(BigInteger.ZERO, BigInteger.valueOf(LOW_PRIME_LENGTH)).intValue();
        for (int i = 0; i < t; ++i, count=(count+1)%LOW_PRIME_LENGTH) {
            // Pick bases at random, instead of starting at 2
            BigInteger a = LOW_PRIME_LIST[count];
            BigInteger y = RSA.modExp(a, r, n);
            if (y.compareTo(BigInteger.ONE) != 0 && y.compareTo(n1) != 0) {
                int j = 1;
                while (j++ < k && y.compareTo(n1) != 0) {
                    y = y.multiply(y).remainder(n);
                    if (y.compareTo(BigInteger.ONE) == 0) {
                        return false;
                    }
                }
                if (y.compareTo(n1) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private BigInteger[] _extendedEuclidean(BigInteger a, BigInteger b){
        BigInteger old_s = BigInteger.ONE, s = BigInteger.ZERO;
        BigInteger old_t = BigInteger.ZERO, t = BigInteger.ONE;
        BigInteger old_r = a, r = b;
        if (b.compareTo(BigInteger.ZERO) == 0) return new BigInteger[]{BigInteger.ONE, BigInteger.ZERO, a};
        else{
            while(r.compareTo(BigInteger.ZERO) != 0){
                BigInteger tmp;
                BigInteger q = old_r.divide(r);
                tmp = r; r = old_r.subtract(q.multiply(r)); old_r = tmp;
                tmp = s; s = old_s.subtract(q.multiply(s)); old_s = tmp;
                tmp = t; t = old_t.subtract(q.multiply(t)); old_t = tmp;
            }
        }
        if(old_t.compareTo(BigInteger.ZERO) < 0) {
            old_t = old_t.remainder(a);
            old_t = old_t.add(a);
        }
        return new BigInteger[]{old_s, old_t, old_r};
    }
    private BigInteger _generatePrimeNumberByProbability(BigInteger n0, BigInteger  n1) throws Exception {
        final int maxIter = 10000;
        for(int i=0; i<maxIter; i++){
            BigInteger prime_candidate = this._getLowLevelPrime(n0, n1);
            if (!this._MillerRabinPrimalityTest(prime_candidate))
                continue;
            else
                return prime_candidate;
        }
        throw new Exception("can not find prime number");
    }

    private BigInteger _getE(BigInteger lambda_n, BigInteger p, BigInteger q) throws Exception {
        //method 1: use 2^16 + 1, ...
        BigInteger[] e_list1_pre = new BigInteger[]{BigInteger.valueOf(65537), BigInteger.valueOf(257), BigInteger.valueOf(17)};
        for(int i=0; i<e_list1_pre.length; i++){
            BigInteger e = e_list1_pre[i];
            if(BigInteger.ONE.compareTo(e) < 0 && e.compareTo(lambda_n) < 0 && lambda_n.remainder(e).compareTo(BigInteger.ZERO) != 0/*since e is prime*/) return e;
        }

        //method 2: use prime number.
        BigInteger a = RSA.gcd(p.subtract(BigInteger.ONE), q.subtract(BigInteger.ONE));
        BigInteger b = (p.subtract(BigInteger.ONE).divide(a));
        BigInteger c = (q.subtract(BigInteger.ONE).divide(a));
        BigInteger maxVal = a.compareTo(b) > 0 ? a : b;
        maxVal = maxVal.compareTo(c) > 0 ? maxVal : c;
        for(int i=0; i<100; i++){
            BigInteger prime = this._getLowLevelPrime(BigInteger.valueOf(65536), maxVal);
            if(this._MillerRabinPrimalityTest(prime) && prime.compareTo(lambda_n) < 0 && lambda_n.remainder(prime).compareTo(BigInteger.ZERO) != 0/*since e is prime*/){
                return prime;
            }
        }
        //method 3:　force.
        BigInteger e = lambda_n.subtract(BigInteger.ONE);
        while (e.compareTo(BigInteger.valueOf(65536)) > 0){
            if (RSA.gcd(e, lambda_n).compareTo(BigInteger.ONE) == 0){
                return e;
            }
            e = e.subtract(BigInteger.ONE);
        }

        throw new Exception("can not find e.");
    }

    public static BigInteger randint(BigInteger start, BigInteger end){
        BigInteger range = end.subtract(start);
        int ln2 = range.bitLength();
        int len = ln2 >> 3;
        if((ln2 & 0b111) != 0) len++;

        byte[] randArr = new byte[len];     RSA.crypto.nextBytes(randArr);
        BigInteger bint = BigInteger.ZERO;


        for(int i=0;i<len;i++){
            int intVal =  randArr[i] & 0xff;
            bint = bint.add(BigInteger.valueOf(intVal).shiftLeft(BigInteger.valueOf(i).shiftLeft(3).intValue()));
        }
        bint = range.multiply(bint).divide(BigInteger.ONE.shiftLeft(BigInteger.valueOf(len).shiftLeft(3).intValue())).add(start);
        return bint;
    }
    public static BigInteger gcd(BigInteger a, BigInteger b){//Greatest Common Divisor Generator (Euclidean Algorithm)
        BigInteger temp;
        while (b.compareTo(BigInteger.ZERO) != 0){
            temp = b;
            b = a.remainder(b);
            a = temp;
        }
        return a;
    }
    public static BigInteger lcm(BigInteger a, BigInteger b){
        return a.multiply(b).divide(RSA.gcd(a, b));
    }
    public static BigInteger log(BigInteger n){
        BigInteger _ln = BigInteger.valueOf( n.bitLength() - 1);
        return _ln.shiftLeft(16).divide(BigInteger.valueOf(94548));
    }
    public static BigInteger modExp(BigInteger x, BigInteger e, BigInteger m){
        BigInteger X = x, E = e, Y = BigInteger.ONE;
        while (E .compareTo(BigInteger.ZERO)> 0){
            if (E.and(BigInteger.ONE).compareTo(BigInteger.ZERO) == 0){
                X = (X.multiply(X)).remainder(m);
                E = E.shiftRight(1);
            }else{
                Y = (X.multiply(Y)).remainder(m);
                E = E.subtract(BigInteger.ONE) ;
            }
        }
        return Y;
    }
}

