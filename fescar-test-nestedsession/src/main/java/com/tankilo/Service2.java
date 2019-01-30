package com.tankilo;

import com.alibaba.fescar.spring.annotation.GlobalTransactional;
import org.springframework.stereotype.Service;

@Service
public class Service2 {

    @GlobalTransactional(timeoutMills = 30000, name = "simple-demo")
    public void doSomething()  {
        System.out.println("doSomething!!!");
    }
}
