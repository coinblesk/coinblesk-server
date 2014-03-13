/*
 * KrotJSON License
 * 
 * Copyright (c) 2013, Mikhail Yevchenko.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the 
 * Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject
 * to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR
 * ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH
 * THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.azazar.krotjson;

/**
 *
 * @author Mikhail Yevchenko <m.ṥῥẚɱ.ѓѐḿởύḙ@azazar.com>
 */
public class StringParser {

    public String string;

    public StringParser(String string) {
        this.string = string;
    }

    public void forward(int chars) {
        string = string.substring(chars);
    }

    public char poll() {
        char c = string.charAt(0);
        forward(1);
        return c;
    }

    public String poll(int length) {
        String str = string.substring(0, length);
        forward(length);
        return str;
    }

    public String pollBeforeSkipDelim(String s) {
        int i = string.indexOf(s);
        if (i == -1)
            throw new RuntimeException("\"" + s + "\" not found in \"" + string + "\"");
        String rv = string.substring(0, i);
        forward(i + s.length());
        return rv;
    }

    public char peek() {
        return string.charAt(0);
    }

    public String peek(int length) {
        return string.substring(0, length);
    }

    public String trim() {
        return string = string.trim();
    }

    public boolean isEmpty() {
        return string.isEmpty();
    }

    @Override
    public String toString() {
        return string;
    }

}
