package com.jeremyliao.gradle;

/**
 * @author yulun
 * @sinice 2021-11-11 10:39
 */
class AsmCodeTest {
    public static void methodEnter(){
        long a = System.currentTimeMillis();
        long b = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        sb.append("cost times is ").append(b-a);
        System.out.println(sb);

    }

    void methodExit(){

    }
}
