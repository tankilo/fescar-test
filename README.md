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
 
## fescar-test-nestedsession
在fescar-test-simple中我发现ThreadLocal泄露的问题，然后提了https://github.com/alibaba/fescar/issues/313    
并且发起了pull request https://github.com/alibaba/fescar/pull/315      
官方没有合入，并且开了https://github.com/alibaba/fescar/issues/325 自己修改了这个。   
我看了下官方改法，嗯，以后我晚上很晚不改代码了，休息休息锻炼身体，开始我也想放在finally里，但是脑子里一闪后我没有。     
但是官方的改法明显有问题，事务嵌套的时候，按照现在的API应该算一个事务，     
但是里层事务退出的时候会把外层给提交或者回滚了，即使外层事务可能是反过来的，会回滚或者提交。  
所以我弄了这个demo。      
  
1. 在mysql里运行fescar-test-simple/src/main/resources/fescar-simple.sql     
可以选择把两个表分到两个mysql，为了测试方便也可以用于一个mysql    
2. 在mysql里运行fescar-test-simple/src/main/resources/undo_log.sql     
3. 修改fescar-test-simple/src/main/resources/jdbc.properties指向对应的库    
4. 启动fescar-server在默认端口      
5. 运行com.tankilo.NestSessionTest    
如果直接运行的话，里面的全局事务提交后，RM还没异步删除undo_log，所以外层事务还能回滚，看起来没有问题。     
所以需要调试，断点选暂停Thread，在里层事务commit等一会儿，等undo_log给清理了，     
然后继续放开断点，就发现外层事务根本没法回滚，因为undo_log都没了。     

## fescar-test-lockconflict
为了求快还是弄了项目来测试对 https://github.com/alibaba/fescar/issues/324 的修改   
这个问题后来发现com.alibaba.fescar.rm.datasource.exec.SelectForUpdateExecutor.doExecute里面是正确处理的，回滚了物理sql。   
如果早看到那里，不用花太多时间。    
其实应该写个单元测试的。
 
1. 在mysql里运行fescar-test-simple/src/main/resources/undo_log.sql   
2. 在com.alibaba.fescar.tm.api.TransactionalTemplate.execute里面`tx.commit()`这行设置断点1    
3. 调试运行com.tankilo.LockConflictTest,进程A,停在断点1处   
4. 在com.alibaba.fescar.rm.datasource.exec.AbstractDMLBaseExecutor.executeAutoCommitTrue里面`result = executeAutoCommitFalse(args);`这行设置断点2   
5. 调试运行com.tankilo.LockConflictTest，进程B   
6. 进程B在while循环里走两次循环  
7. 进程1放过断点1，等待全局事务提交完成，undo_log清理完毕  
8. 进程2放过断点2

确保money的值是9998.   
