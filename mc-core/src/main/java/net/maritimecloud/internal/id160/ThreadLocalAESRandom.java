/*
 * Copyright (c) 2008 Kasper Nielsen.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.maritimecloud.internal.id160;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ThreadLocalRandom;

import javax.crypto.Cipher;

import net.maritimecloud.util.Id160;

/**
 * A secure random number generator isolated to the current thread. This generator is similar to
 * {@link ThreadLocalRandom} except this generator uses an AES {@link Cipher} for generating values. Like
 * {@link SecureRandom} this random generate numbers that are cryptographically secure. But it scales much better when
 * contended.
 * <p>
 * {@code ThreadLocalAESRandom} is initialized with an internally generated secure seed that may not otherwise be
 * modified. When applicable, use of {@code ThreadLocalAESRandom} rather than shared {@code SecureRandom} objects in
 * concurrent programs will typically encounter much less overhead and contention. Use of {@code ThreadLocalAESRandom}
 * is particularly appropriate when multiple tasks (for example, each a {@link ForkJoinTask}) use random numbers in
 * parallel in thread pools.
 * <p>
 * Usages of this class should typically be of the form: {@code ThreadLocalAESRandom().nextX(...)} (where {@code X} is
 * {@code Int}, {@code Long}, etc). When all usages are of this form, it is never possible to accidently share a
 * {@code ThreadLocalAESRandom} across multiple threads.
 * <p>
 * This class also provides additional commonly used bounded random generation methods and methods that generate various
 * types and ID's.
 * <p>
 * Compared to {@link SecureRandom} this class is roughly 3-4 times faster. Even in single threaded tests.
 *
 * @author Kasper Nielsen
 * @see ThreadLocalRandom
 * @see ThreadLocalMersenneTwisterRandom
 * @see SecureRandom
 */
public class ThreadLocalAESRandom extends Random {

    /** The default number of bytes to use as the seed. */
    private static final int DEFAULT_SEED_SIZE = 16;

    private final boolean initialized;

    /** The actual ThreadLocal */
    private static final ThreadLocal<ThreadLocalAESRandom> LOCAL_RANDOM = new ThreadLocal<ThreadLocalAESRandom>() {
        protected ThreadLocalAESRandom initialValue() {
            return new ThreadLocalAESRandom();
        }
    };

    /** serialVersionUID */
    private static final long serialVersionUID = -3965105704619791433L;

    /** The cipher used for generating random blocks. */
    private final transient Cipher cipher;

    /**
     * A counter used internally for generate new numbers, the idea is just to increment it each time and then encrypt
     * it. The counter is initialized to a random secure number.
     */
    private final transient byte[] counter = new byte[16]; // 128-bit counter.

    /** The cache of generated byes. */
    private transient byte[] cache;

    /** The current index into the cache */
    private transient int cacheIndex;

    // Padding to help avoid memory contention among seed updates in
    // different TLMTRs in the common case that they are located near
    // each other.
    // Not that important for ThreadLocalAESRandom as ThreadLocalMersenneTwisterRandom as it is roughly
    // A magnitude slower than ThreadLocalMersenneTwisterRandom
    @SuppressWarnings("unused")
    private int pad1, pad2, pad3, pad4, pad5, pad6, pad7, pad8, pad9, pad10, pad11, pad12, pad13, pad14, pad15, pad16;

    /** Whether or not we have cached the next gaussian value. */
    private transient boolean haveNextGaussian;

    /** If haveNextGaussian == true, the next gaussian value to return from {@link #nextGaussian()}. */
    private transient double nextGaussian;

    /** Creates a new ThreadLocalAESRandom. */
    ThreadLocalAESRandom() {
        try {
            cipher = Cipher.getInstance("AES/ECB/NoPadding");
        } catch (GeneralSecurityException e) {
            // /CLOVER:OFF
            throw new Error(e);
            // /CLOVER:ON
        }
        seed(RandomUtil.secureRandom(DEFAULT_SEED_SIZE), RandomUtil.secureRandom(counter.length));
        initialized = true;
    }

    /** {@inheritDoc} */
    @Override
    protected final int next(int bits) {
        byte[] currentBlock = this.cache;
        if (currentBlock.length - cacheIndex < 4) {
            try {
                currentBlock = nextBlock();
            } catch (GeneralSecurityException ex) {
                // Should never happen. If initialization succeeds without exceptions
                // we should be able to proceed indefinitely without exceptions.
                // /CLOVER:OFF
                throw new InternalError("Failed creating next random block.", ex);
                // /CLOVER:ON
            }
        }
        int result = BinaryUtil.readInt(currentBlock, cacheIndex);
        cacheIndex += 4;
        return result >>> (32 - bits);
    }

    /**
     * Generates a single 128-bit block (16 bytes).
     *
     * @throws GeneralSecurityException
     *             If there is a problem with the cipher that generates the random data.
     * @return A 16-byte block of random data.
     */
    private byte[] nextBlock() throws GeneralSecurityException {
        cacheIndex = 0;
        if (++counter[0] != 0) {
            return cache = cipher.doFinal(counter);
        }
        // counter[0] overflowed from -1 to 0
        for (int i = 1; i < counter.length; i++) {
            if (++counter[i] != 0) {
                return cache = cipher.doFinal(counter);
            }
        }
        // all counter[x] has overflown from -1 to 0
        return cache = cipher.doFinal(counter);
    }

    /**
     * Returns a pseudorandom, uniformly distributed {@code double} value between 0 (inclusive) and the specified value
     * (exclusive).
     *
     * @param n
     *            the bound on the random number to be returned. Must be positive.
     * @return the next value
     * @throws IllegalArgumentException
     *             if n is not positive
     */
    public double nextDouble(double n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive");
        }
        return nextDouble() * n;
    }

    /**
     * Returns a pseudorandom, uniformly distributed value between the given least value (inclusive) and bound
     * (exclusive).
     *
     * @param least
     *            the least value returned
     * @param bound
     *            the upper bound (exclusive)
     * @return the next value
     * @throws IllegalArgumentException
     *             if least greater than or equal to bound
     */
    public double nextDouble(double least, double bound) {
        if (least >= bound) {
            throw new IllegalArgumentException();
        }
        return nextDouble() * (bound - least) + least;
    }

    /** {@inheritDoc} */
    public double nextGaussian() {
        // Based on Art of Computer Programming, Volume 2: Seminumerical Algorithms</i>, section 3.4.1, subsection C,
        // algorithm P.
        // Based on version from apache harmorny
        // http://svn.apache.org/viewvc/harmony/enhanced/java/trunk/classlib/modules/luni/src/main/java/java/util/Random.java?revision=929253
        if (haveNextGaussian) { // if X1 has been returned, return the second Gaussian
            haveNextGaussian = false;
            return nextGaussian;
        }
        double v1, v2, s;
        do {
            v1 = 2 * nextDouble() - 1; // Generates two independent random variables U1, U2
            v2 = 2 * nextDouble() - 1;
            s = v1 * v1 + v2 * v2;
        } while (s >= 1);
        double norm = Math.sqrt(-2 * Math.log(s) / s);
        nextGaussian = v2 * norm;
        haveNextGaussian = true;
        return v1 * norm;
    }

    /**
     * Returns a random string consisting only of numerals <tt>0-9</tt> and the Latin letters <tt>A-F</tt>
     *
     * @param length
     *            the length of the string to generate
     * @return the random string
     */
    public String nextHexString(int length) {
        return HexStringUtil.next(this, length);
    }

    /**
     * Returns a pseudorandom, uniformly distributed {@link Id160} value using this as a random source.
     *
     * @return the new pseudorandom, uniformly distributed Id160
     * @see Id160#random(Random)
     */
    public Id160 nextId160() {
        return Id160.random(this);
    }

    /**
     * Returns a pseudorandom, uniformly distributed value between {@link Id160#ZERO} (inclusive) and the specified
     * value (exclusive).
     *
     * @param bound
     *            the bound on the random number to be returned.
     * @return the next value
     * @see Id160#random(Random, Id160)
     * @throws NullPointerException
     *             if the specified bound is null
     */
    public Id160 nextId160(Id160 bound) {
        return Id160.random(this, bound);
    }

    /**
     * Returns a pseudorandom, uniformly distributed value between the given least value (inclusive) and bound
     * (exclusive).
     *
     * @param least
     *            the least value returned
     * @param bound
     *            the upper bound (exclusive)
     * @return a random bounded value
     * @throws IllegalArgumentException
     *             if least is greater than or equal to bound
     * @throws NullPointerException
     *             if the specified bound or least is null
     */
    public Id160 nextId160(Id160 least, Id160 bound) {
        return Id160.random(this, least, bound);
    }

    /**
     * Returns a pseudorandom, uniformly distributed value between the given least value (inclusive) and bound
     * (exclusive).
     *
     * @param least
     *            the least value returned
     * @param bound
     *            the upper bound (exclusive)
     * @throws IllegalArgumentException
     *             if least greater than or equal to bound
     * @return the next value
     */
    public int nextInt(int least, int bound) {
        if (least >= bound || least <= 0) {
            throw new IllegalArgumentException();
        }
        return nextInt(bound - least) + least;
    }

    /**
     * Returns a pseudorandom, uniformly distributed value between 0 (inclusive) and the specified value (exclusive).
     *
     * @param n
     *            the bound on the random number to be returned. Must be positive.
     * @return the next value
     * @throws IllegalArgumentException
     *             if n is not positive
     */
    public long nextLong(long n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive");
        }
        // Divide n by two until small enough for nextInt. On each
        // iteration (at most 31 of them but usually much less),
        // randomly choose both whether to include high bit in result
        // (offset) and whether to continue with the lower vs upper
        // half (which makes a difference only if odd).
        long offset = 0;
        while (n >= Integer.MAX_VALUE) {
            int bits = next(2);
            long half = n >>> 1;
            long nextn = ((bits & 2) == 0) ? half : n - half;
            if ((bits & 1) == 0) {
                offset += n - nextn;
            }
            n = nextn;
        }
        return offset + nextInt((int) n);
    }

    /**
     * Returns a pseudorandom, uniformly distributed value between the given least value (inclusive) and bound
     * (exclusive).
     *
     * @param least
     *            the least value returned
     * @param bound
     *            the upper bound (exclusive)
     * @return the next value
     * @throws IllegalArgumentException
     *             if least greater than or equal to bound
     */
    public long nextLong(long least, long bound) {
        if (least >= bound || least <= 0) {
            throw new IllegalArgumentException();
        }
        return nextLong(bound - least) + least;
    }

    /**
     * Returns a new type 4 (pseudo randomly generated) cryptographically secure UUID.
     *
     * @return the new UUID
     * @see UUID#randomUUID()
     */
    public UUID nextUUID() {
        return RandomUtil.longsToType4UUID(nextLong(), nextLong());
    }

    /** @return Preserves singleton property */
    private Object readResolve() {
        return new ThreadLocalAESRandom();
    }

    /**
     * Initializes the random with the specified seed and counter.
     *
     * @param seed
     *            the seed (seedInts.length must be 16)
     * @param counter
     *            the initial counter (counter.length must be 16)
     */
    @SuppressWarnings("serial")
    private void seed(final byte[] seed, final byte[] counter) {
        System.arraycopy(counter, 0, this.counter, 0, counter.length);
        try {
            cipher.init(Cipher.ENCRYPT_MODE, new Key() {
                public String getAlgorithm() {
                    return "AES";
                }

                public byte[] getEncoded() {
                    return seed;
                }

                public String getFormat() {
                    return "RAW";
                }
            });
            cache = nextBlock();// we will initialize first block
        } catch (GeneralSecurityException e) {
            // /CLOVER:OFF
            throw new Error(e);
            // /CLOVER:ON
        }
    }

    /**
     * Throws {@code UnsupportedOperationException}. Setting seeds in this random is not supported.
     *
     * @throws UnsupportedOperationException
     *             always
     */
    public void setSeed(long seed) {
        if (initialized) {// This is needed for the first release of 1.7 which calls setSeed from Random() constructor
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Returns the current thread's {@code ThreadLocalAESRandom}.
     *
     * @return the current thread's {@code ThreadLocalAESRandom}
     */
    public static ThreadLocalAESRandom current() {
        return LOCAL_RANDOM.get();
    }

    // @Override
    // public IntStream ints() {
    // return IntStream.generate(new IntSupplier() {
    // public int getAsInt() {
    // return nextInt();
    // }
    // });
    // }
    //
    // @Override
    // public LongStream longs() {
    // return LongStream.generate(new LongSupplier() {
    // public long getAsLong() {
    // return nextLong();
    // }
    // });
    // }
    //
    // @Override
    // public DoubleStream doubles() {
    // return DoubleStream.generate(new DoubleSupplier() {
    // public double getAsDouble() {
    // return nextInt();
    // }
    // });
    // }
    //
    // @Override
    // public DoubleStream gaussians() {
    // return DoubleStream.generate(new DoubleSupplier() {
    // public double getAsDouble() {
    // return nextGaussian();
    // }
    // });
    // }
}
