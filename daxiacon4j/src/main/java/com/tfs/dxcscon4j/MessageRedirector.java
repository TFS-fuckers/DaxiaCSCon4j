package com.tfs.dxcscon4j;

/**
 * 函数式接口
 * 用于区别不同类型的记录信息，用户可以根据自己的需求对信息进行重定向
 */
@FunctionalInterface
public interface MessageRedirector {
    public void log(String message, String head);
}
