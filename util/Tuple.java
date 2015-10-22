package PSaPP.util;

/*
Copyright (c) 2011, PMaC Laboratories, Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are
permitted provided that the following conditions are met:

 *  Redistributions of source code must retain the above copyright notice, this list of conditions
and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice, this list of conditions
and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *  Neither the name of PMaC Laboratories, Inc. nor the names of its contributors may be
used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

public class Tuple {
    public static interface Tuple1<A> { public A get1(); }
    public static interface Tuple2<A, B> extends Tuple1<A> { public B get2(); }
    public static interface Tuple3<A, B, C> extends Tuple2<A, B> { public C get3(); }
    public static interface Tuple4<A, B, C, D> extends Tuple3<A, B, C> { public D get4(); };
    public static interface Tuple5<A, B, C, D, E> extends Tuple4<A, B, C, D> { public E get5(); };

    public static <A> Tuple1<A> newTuple1(final A a) {
        return new Tuple1<A>() {
            public A get1() { return a; }
        };
    }

    public static <A, B> Tuple2<A, B> newTuple2(final A a, final B b) {
        return new Tuple2<A, B>() {
            public A get1() { return a; }
            public B get2() { return b; }
        };
    }

    public static <A, B, C> Tuple3<A, B, C> newTuple3(final A a, final B b, final C c) {
        return new Tuple3<A, B, C>() {
            public A get1() { return a; }
            public B get2() { return b; }
            public C get3() { return c; }
        };
    }

    public static <A, B, C, D> Tuple4<A, B, C, D> newTuple4(final A a, final B b, final C c, final D d) {
        return new Tuple4<A, B, C, D>() {
            public A get1() { return a; }
            public B get2() { return b; }
            public C get3() { return c; }
            public D get4() { return d; }
        };
    }

    public static <A, B, C, D, E> Tuple5<A, B, C, D, E> newTuple5(final A a, final B b, final C c, final D d, final E e) {
        return new Tuple5<A, B, C, D, E>() {
            public A get1() { return a; }
            public B get2() { return b; }
            public C get3() { return c; }
            public D get4() { return d; }
            public E get5() { return e; }
        };
    }
}
