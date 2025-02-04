/*
 * Copyright (C) 2020 Xiaomi Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.ozhera.log.stream;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author wtt
 * @version 1.0
 * @description
 * @date 2021/12/28 17:09
 */
public class PatterTest {

    @Test
    public void test1() {
        Object timestampObject = "1.655659893004E12";
        System.out.println(String.valueOf(timestampObject).length());

        String content = "I am pratice from runoob.com";
        String regex = ".*runoob.*";

        boolean isMatch = Pattern.matches(regex, content);
        System.out.println("Whether the string contains “runoob”" + isMatch);
    }

    @Test
    public void test2() {
        //Looks in a string by the specified pattern
        String input = "this order was placed for QT3000! OK? ";
        String regex = "(\\D*)(\\d+)(.*)";
        //Create a Pattern object
        Pattern p = Pattern.compile(regex);
        //Create a matcher object
        Matcher m = p.matcher(input);
        if (m.find()) {
            System.out.println("Found value:" + m.group(0));
            System.out.println("Found value:" + m.group(1));
            System.out.println("Found value:" + m.group(2));
            System.out.println("Found value:" + m.group(3));
        } else {
            System.out.println("no matcher");
        }
    }

    @Test
    public void test3() {
        String regex = "\\bcat\\b";
        final String input = "cat cat cat cattie cat";
        //Create a Pattern object
        Pattern p = Pattern.compile(regex);
        //Create a Pattern object...
        Matcher m = p.matcher(input);
        int count = 0;
        if (m.find()) {
            count++;
            System.out.println(" Match number" + count);
            System.out.println("start()" + m.start());
            System.out.println("end()" + m.end());
        }
    }
}
