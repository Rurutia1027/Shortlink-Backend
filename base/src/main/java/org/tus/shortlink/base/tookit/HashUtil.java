package org.tus.shortlink.base.tookit;

import cn.hutool.core.lang.hash.MurmurHash;

public class HashUtil {

    // Characters used for Base62 encoding
    private static final char[] CHARS = new char[]{
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'
    };
    private static final int SIZE = CHARS.length;

    /**
     * Convert a decimal number to a Base62 string
     *
     * @param num the decimal number
     * @return Base62 encoded string
     */
    private static String convertDecToBase62(long num) {
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            int i = (int) (num % SIZE);
            sb.append(CHARS[i]);
            num /= SIZE;
        }
        return sb.reverse().toString();
    }

    /**
     * Hash a string using MurmurHash32 and convert the result to Base62
     *
     * @param str input string
     * @return Base62 encoded hash string
     */
    public static String hashToBase62(String str) {
        int i = MurmurHash.hash32(str); // compute 32-bit hash
        long num = i < 0 ? Integer.MAX_VALUE - (long) i : i; // handle negative hash values
        return convertDecToBase62(num);
    }
}
