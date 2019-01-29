# fescar-test
这个项目修改[阿里云 GTS的样例工程](https://help.aliyun.com/document_detail/57267.html)    
来测试GTS的开源版本 [fescar](https://github.com/alibaba/fescar)

## fescar-test-simple
对应GTS的sample-txc-simple  
1. 在mysql里运行fescar-test-simple/src/main/resources/fescar-simple.sql
可以选择把两个表分到两个mysql，为了测试方便也可以用于一个mysql
2. 在mysql里运行fescar-test-simple/src/main/resources/undo_log.sql
3. 修改fescar-test-simple/src/main/resources/jdbc.properties指向对应的库
4. 启动fescar-server在默认端口
5. 运行fescar-test-simple/src/main/java/com/tankilo/Simple.java

**注意事项1**      
直接运行会遇到[issue312](https://github.com/alibaba/fescar/issues/312) 的问题，需要修改com.tankilo.Simple#transfer加个条件
```
        jdbcTemplate1.update("update user_money_a set money = money - ?", money);
        
        jdbcTemplate2.update("update user_money_b  set money = money + ?", money);
```
修改成
```
        jdbcTemplate1.update("update user_money_a set money = money - ? where 1=1", money);
        
        jdbcTemplate2.update("update user_money_b  set money = money + ? where 1=1", money);
```
**注意事项2**     
继续运行你会遇到[issue313](https://github.com/alibaba/fescar/issues/313)
