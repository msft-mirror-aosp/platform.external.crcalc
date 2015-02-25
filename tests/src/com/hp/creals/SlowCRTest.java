/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* THIS TYPICALLY TAKES > 10 MINUTES TO RUN!  It shold generate no output during that time. */

package com.hp.creals;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

import java.math.BigInteger;
import java.util.Random;

public class SlowCRTest extends TestCase {
    private static void check(boolean x, String s) {
        if (!x) throw new AssertionFailedError(s);
    }
    final static int TEST_PREC = -200; // 200 bits to the right of
                                       // binary point.
    final static int NRANDOM = 100;    // Number of random values to
                                       // test.  Bigger ==> slower
    private static void checkEq(CR x, CR y, String s) {
        if (x.compareTo(y, TEST_PREC) != 0) throw new AssertionFailedError(s);
    }
    private static void checkApprEq(CR x, CR y, String s) {
        BigInteger abs_difference = x.subtract(y).get_appr(TEST_PREC).abs();
        if (abs_difference.compareTo(BigInteger.ONE) > 0)
                throw new AssertionFailedError(s);
    }
    private static void checkApprEq(double x, double y, String s) {
        if (Math.abs(x - y) > 0.000001) throw new AssertionFailedError(s);
    }
    final static BigInteger MASK =
            BigInteger.ONE.shiftLeft(-TEST_PREC).subtract(BigInteger.ONE);
    private static boolean isApprInt(CR x) {
        BigInteger appr = x.get_appr(TEST_PREC);
        return appr.and(MASK).signum() == 0;
    }

    final static CR ZERO = CR.valueOf(0);
    final static CR ONE = CR.valueOf(1);
    final static CR TWO = CR.valueOf(2);
    final static CR BIG = CR.valueOf(200).exp();
    final static CR SMALL = BIG.inverse();

    final static UnaryCRFunction ASIN = UnaryCRFunction.asinFunction;
    final static UnaryCRFunction ACOS = UnaryCRFunction.acosFunction;
    final static UnaryCRFunction ATAN = UnaryCRFunction.atanFunction;
    final static UnaryCRFunction TAN = UnaryCRFunction.tanFunction;
    final static UnaryCRFunction COSINE = UnaryCRFunction.sinFunction
                                             .monotoneDerivative(ZERO, CR.PI);

    // Perform some consistency checks on trig functions at x
    // We assume that x is within floating point range.
    private static void checkTrig(CR x) {
        double xAsDouble = x.doubleValue();
        if (Math.abs(xAsDouble) < 1000000.0) {
            checkApprEq(x.sin().doubleValue(), Math.sin(xAsDouble),
                          "sin float compare:" + xAsDouble);
            checkApprEq(x.cos().doubleValue(), Math.cos(xAsDouble),
                          "cos float compare:" + xAsDouble);
            checkApprEq(TAN.execute(x).doubleValue(), Math.tan(xAsDouble),
                          "tan float compare:" + xAsDouble);
            checkApprEq(ATAN.execute(x).doubleValue(), Math.atan(xAsDouble),
                          "atan float compare:" + xAsDouble);
        }
        if (Math.abs(xAsDouble) < 1.0) {
            checkApprEq(ASIN.execute(x).doubleValue(), Math.asin(xAsDouble),
                          "asin float compare:" + xAsDouble);
            checkApprEq(ACOS.execute(x).doubleValue(), Math.acos(xAsDouble),
                          "acos float compare:" + xAsDouble );
        }
        if (xAsDouble < 3.1415926535 && xAsDouble > 0.0) {
            checkApprEq(COSINE.execute(x).doubleValue(), Math.cos(xAsDouble),
                          "deriv(sin) float compare:"  + xAsDouble);
            checkApprEq(COSINE.execute(x), x.cos(),
                          "deriv(sin) float compare:"  + xAsDouble);
        }
        // Check that sin(x+v) = sin(x)cos(v) + cos(x)sin(v)
        // for a couple of different values of v.
        for (int i = 1; i <= 5; ++i) {
            CR v = CR.valueOf(i);
            checkApprEq(
                x.add(v).sin(),
                x.sin().multiply(v.cos()).add(x.cos().multiply(v.sin())),
                "Angle sum formula failed for " + xAsDouble + " + " + i);
        }
        checkApprEq(x.cos().multiply(x.cos()).add(x.sin().multiply(x.sin())),
                    CR.valueOf(1),
                    "sin(x)^2 + cos(x)^2 != 1:" + xAsDouble);
        // Check that inverses are consistent
        checkApprEq(x, TAN.execute(ATAN.execute(x)),
                      "tan(atan(" + xAsDouble + ")" );
        CR tmp = ACOS.execute(x.cos());
        // Result or its inverse should differ from x by an
        // exact multiple of pi.
        check(isApprInt(tmp.subtract(x).divide(CR.PI))
              || isApprInt(tmp.add(x).divide(CR.PI)),
              "acos(cos):" + xAsDouble);
        tmp = ASIN.execute(x.sin());
        // Result or its inverse should differ from x by an
        // exact multiple of pi.
        check(isApprInt(tmp.subtract(x).divide(CR.PI))
              || isApprInt(tmp.add(x).divide(CR.PI)),
              "acos(cos):" + xAsDouble);
    }

    private static void checkExpLn(CR x) {
        double xAsDouble = x.doubleValue();
        if (Math.abs(xAsDouble) < 10.0) {
            checkApprEq(x.exp().doubleValue(), Math.exp(xAsDouble),
                          "exp float compare:" + xAsDouble);
        }
        if (Math.abs(xAsDouble) <= 1000.0) {
            checkApprEq(x, x.exp().ln(), "ln(exp) failed:" + xAsDouble);
            checkApprEq(x.multiply(CR.valueOf(2)).exp(),

                        x.exp().multiply(x.exp()),
                        "exp^2 failed:" + xAsDouble);
        }
        if (xAsDouble > 0.000000001) {
            checkApprEq(x.ln().doubleValue(), Math.log(xAsDouble),
                          "exp float compare:" + xAsDouble);
            checkApprEq(x, x.ln().exp(), "exp(ln) failed:" + xAsDouble);
            checkApprEq(x.ln().divide(CR.valueOf(2)), x.sqrt().ln(),
                        "ln(sqrt) failed:" + xAsDouble);
            // Check that ln(xv) = ln(x) + ln(v) for various v
            for (int i = 1; i <= 5; ++i) {
                CR v = CR.valueOf(i);
                checkApprEq(
                    x.ln().add(v.ln()),
                    x.multiply(v).ln(),
                    "ln(product) formula failed for:" + xAsDouble + "," + i);
            }
        }
    }

    private static void checkBasic(CR x) {
        checkApprEq(x.abs().sqrt().multiply(x.abs().sqrt()), x.abs(),
                    "sqrt*sqrt:" + x.doubleValue());
        if (!x.get_appr(TEST_PREC).equals(BigInteger.ZERO)) {
            checkApprEq(x.inverse().inverse(), x,
                        "inverse(inverse):" + x.doubleValue());
        }
    }

    public void testSlowTrig() {
        checkApprEq(ACOS.execute(ZERO), CR.PI.divide(TWO), "acos(0)");
        checkApprEq(ACOS.execute(ONE), ZERO, "acos(1)");
        checkApprEq(ACOS.execute(ONE.negate()), CR.PI, "acos(-1)");
        checkApprEq(ASIN.execute(ZERO), ZERO, "asin(0)");
        checkApprEq(ASIN.execute(ONE), CR.PI.divide(TWO), "asin(1)");
        checkApprEq(ASIN.execute(ONE.negate()),
                                 CR.PI.divide(TWO).negate(), "asin(-1)");
        checkTrig(ZERO);
        CR BIG = CR.valueOf(200).exp();
        checkTrig(BIG);
        checkTrig(BIG.negate());
        checkTrig(SMALL);
        checkTrig(SMALL.negate());
        checkTrig(CR.PI);
        checkTrig(CR.PI.subtract(SMALL));
        checkTrig(CR.PI.add(SMALL));
        checkTrig(CR.PI.negate());
        checkTrig(CR.PI.negate().subtract(SMALL));
        checkTrig(CR.PI.negate().add(SMALL));
        Random r = new Random();  // Random seed!
        for (int i = 0; i < NRANDOM; ++i) {
            double d = Math.exp(2.0 * r.nextDouble() - 1.0);
            if (r.nextBoolean()) d = -d;
            final CR x = CR.valueOf(d);
            checkTrig(x);
        }
        // And a few big ones
        for (int i = 0; i < 10; ++i) {
            double d = Math.exp(200.0 * r.nextDouble());
            if (r.nextBoolean()) d = -d;
            final CR x = CR.valueOf(d);
            checkTrig(x);
        }
    }

    public void testSlowExpLn() {
        checkApprEq(CR.valueOf(1).ln(), CR.valueOf(0), "ln(1) != 0");
        checkExpLn(CR.valueOf(0));
        CR BIG = CR.valueOf(200).exp();
        checkExpLn(BIG);
        checkExpLn(BIG.negate());
        checkExpLn(SMALL);
        checkExpLn(SMALL.negate());
        checkExpLn(CR.PI);
        checkExpLn(ONE);
        checkExpLn(ONE.subtract(SMALL));
        checkExpLn(ONE.negate().subtract(SMALL));
        Random r = new Random();  // Random seed!
        for (int i = 0; i < NRANDOM; ++i) {
            double d = Math.exp(10.0 * r.nextDouble() - 1.0);
            if (r.nextBoolean()) d = -d;
            final CR x = CR.valueOf(d);
            checkExpLn(x);
        }
        // And a few big ones
        for (int i = 0; i < 10; ++i) {
            double d = Math.exp(200.0 * r.nextDouble());
            if (r.nextBoolean()) d = -d;
            final CR x = CR.valueOf(d);
            checkExpLn(x);
        }
    }

    public void testSlowBasic() {
        checkApprEq(ZERO.sqrt(), ZERO, "sqrt(0)");
        checkApprEq(ZERO.abs(), ZERO, "abs(0)");
        Random r = new Random();  // Random seed!
        for (int i = 0; i < NRANDOM; ++i) {
            double d = Math.exp(10.0 * r.nextDouble() - 1.0);
            if (r.nextBoolean()) d = -d;
            final CR x = CR.valueOf(d);
            checkBasic(x);
        }
        // And a few very big ones, but within IEEE double range
        for (int i = 0; i < 10; ++i) {
            double d = Math.exp(600.0 * r.nextDouble());
            if (r.nextBoolean()) d = -d;
            final CR x = CR.valueOf(d);
            checkBasic(x);
        }
    }
}
