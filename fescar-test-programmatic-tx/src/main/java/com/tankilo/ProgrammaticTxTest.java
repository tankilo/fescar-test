package com.tankilo;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.fescar.common.exception.ShouldNeverHappenException;
import com.alibaba.fescar.common.thread.NamedThreadFactory;
import com.alibaba.fescar.core.exception.TransactionException;
import com.alibaba.fescar.rm.RMClientAT;
import com.alibaba.fescar.rm.datasource.DataSourceProxy;
import com.alibaba.fescar.tm.TMClient;
import com.alibaba.fescar.tm.api.FailureHandler;
import com.alibaba.fescar.tm.api.GlobalTransaction;
import com.alibaba.fescar.tm.api.GlobalTransactionContext;
import com.alibaba.fescar.tm.api.TransactionalExecutor;
import com.alibaba.fescar.tm.api.TransactionalTemplate;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProgrammaticTxTest {

    private static DataSourceProxy dataSourceProxy;

    public static void main(String[] args) throws Throwable {
        DruidDataSource druidDataSource = new DruidDataSource();
        druidDataSource.setUrl("jdbc:mysql://localhost:3306/fescar");
        druidDataSource.setUsername("fescar");
        druidDataSource.setPassword("fescar");
        druidDataSource.setInitialSize(5);
        druidDataSource.init();

        dataSourceProxy = new DataSourceProxy(druidDataSource);

        initDB();

        ProgrammaticTxTest app = new ProgrammaticTxTest();
        app.demoByHighLevelAPI();
//        app.demoByHighLevelAPI();

        System.in.read();
    }

    private void init() {
        String applicationId = "my_app";
        String transactionServiceGroup = "my_test_tx_group";
        TMClient.init(applicationId, transactionServiceGroup);
        RMClientAT.init(applicationId, transactionServiceGroup);
    }

    private static void initDB() throws SQLException {
        Connection con = null;
        try {
            con = dataSourceProxy.getConnection().getTargetConnection();
            try (Statement st = con.createStatement()) {
                st.executeUpdate("truncate user_money_a");
            }
            try (Statement st = con.createStatement()) {
                st.executeUpdate("truncate user_money_b");
            }
            try (Statement st = con.createStatement()) {
                st.executeUpdate("insert into user_money_a(money) values(10000)");
            }
            try (Statement st = con.createStatement()) {
                st.executeUpdate("insert into user_money_b(money) values(10000)");
            }
        } finally {
            if (null != con) {
                con.close();
            }
        }
    }

    /**
     * AutoCommit is false.
     * One Branch Transaction contains two sql.
     * @throws SQLException
     */
    private static void businessCall1() throws SQLException {
        Connection con = null;
        try {
            con = dataSourceProxy.getConnection();
            con.setAutoCommit(false);
            try (PreparedStatement pt = con.prepareStatement("update user_money_a set money = money - ? where 1=1")) {
                pt.setInt(1, 1);
                pt.executeUpdate();
            }

            try (PreparedStatement pt = con.prepareStatement("update user_money_b set money = money + ? where 1=1")) {
                pt.setInt(1, 1);
                pt.executeUpdate();
            }
            con.commit();
        } catch (Exception e) {
            e.printStackTrace();
            con.rollback();
        } finally {
            if (null != con) {
                con.close();
            }
        }
    }

    /**
     * AutoCommit is True.
     * Two Branch Transaction, each one contains a sql.
     * @throws SQLException
     */
    private static void businessCall2() throws SQLException {
        Connection con = null;
        try {
            con = dataSourceProxy.getConnection();
            try (PreparedStatement pt = con.prepareStatement("update user_money_a set money = money - ? where 1=1")) {
                pt.setInt(1, 1);
                pt.executeUpdate();
            }

            try (PreparedStatement pt = con.prepareStatement("update user_money_b set money = money + ? where 1=1")) {
                pt.setInt(1, 1);
                pt.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != con) {
                con.close();
            }
        }
    }

    /**
     * Demo code for High Level API (TransactionalTemplate) usage.
     *
     * @throws Throwable business exception
     */
    public void demoByHighLevelAPI() throws Throwable {
        // 0. init
        init();

        // 0.1 prepare for the template instance
        TransactionalTemplate transactionalTemplate = new TransactionalTemplate();

        // 0.2 prepare for the failure handler (this is optional)
        FailureHandler failureHandler = new MyFailureHandler();

        try {
            // run you business in template
            transactionalTemplate.execute(new TransactionalExecutor() {
                @Override
                public Object execute() throws Throwable {
                    // Do Your BusinessService
                    businessCall2();
                    return null;
                }

                @Override
                public int timeout() {
                    return 300000;
                }

                @Override
                public String name() {
                    return "my_tx_instance";
                }
            });
        } catch (TransactionalExecutor.ExecutionException e) {
            TransactionalExecutor.Code code = e.getCode();
            switch (code) {
                case RollbackDone:
                    throw e.getOriginalException();
                case BeginFailure:
                    failureHandler.onBeginFailure(e.getTransaction(), e.getCause());
                    throw e.getCause();
                case CommitFailure:
                    failureHandler.onCommitFailure(e.getTransaction(), e.getCause());
                    throw e.getCause();
                case RollbackFailure:
                    failureHandler.onRollbackFailure(e.getTransaction(), e.getCause());
                    throw e.getCause();
                default:
                    throw new ShouldNeverHappenException("Unknown TransactionalExecutor.Code: " + code);

            }
        }

    }

    private static class MyFailureHandler implements FailureHandler {

        private static final Logger LOGGER = LoggerFactory.getLogger(MyFailureHandler.class);

        @Override
        public void onBeginFailure(GlobalTransaction tx, Throwable cause) {
            LOGGER.warn("Failed to begin transaction. ", cause);

        }

        @Override
        public void onCommitFailure(final GlobalTransaction tx, Throwable cause) {
            LOGGER.warn("Failed to commit transaction[" + tx.getXid() + "]", cause);
            final ScheduledExecutorService schedule = new ScheduledThreadPoolExecutor(1,
                new NamedThreadFactory("BusinessRetryCommit", 1, true));
            schedule.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    try {
                        tx.commit();
                        schedule.shutdownNow();

                    } catch (TransactionException ignore) {

                    }

                }
            }, 0, 5, TimeUnit.SECONDS);

        }

        @Override
        public void onRollbackFailure(final GlobalTransaction tx, Throwable cause) {
            LOGGER.warn("Failed to rollback transaction[" + tx.getXid() + "]", cause);
            final ScheduledExecutorService schedule = new ScheduledThreadPoolExecutor(1,
                new NamedThreadFactory("BusinessRetryRollback", 1, true));
            schedule.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    try {
                        tx.rollback();
                        schedule.shutdownNow();

                    } catch (TransactionException ignore) {

                    }

                }
            }, 0, 5, TimeUnit.SECONDS);
        }
    }

    public void demoByLowLevelAPI() throws Throwable {
        // 0. init
        init();

        // 1. get or create a transaction
        GlobalTransaction tx = GlobalTransactionContext.getCurrentOrCreate();

        // 2. begin transaction
        try {
            tx.begin(30000, "my_tx_instance");

        } catch (TransactionException txe) {
            // TODO: Handle the transaction begin failure.
        }

        Object rs = null;
        try {
            // Do Your BusinessService
            businessCall1();
        } catch (Throwable ex) {
            // 3. any business exception, rollback.
            try {
                tx.rollback();
                // 3.1 throw the business exception out.
                throw ex;

            } catch (TransactionException txe) {
                // TODO: Handle the transaction rollback failure.
            }
        }

        // 4. everything is fine, commit.
        try {
            tx.commit();
        } catch (TransactionException txe) {
            // TODO: Handle the transaction rollback failure.

        }
    }
}
