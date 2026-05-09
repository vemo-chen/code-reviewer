package com.vemo.codereview.review;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.vemo.codereview.review.service.ReviewTaskDispatcher;
import com.vemo.codereview.review.service.ReviewTaskWorker;
import java.util.concurrent.Executor;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ReviewTaskDispatcherTest {

    @Test
    void shouldDeferDispatchUntilTransactionCommitted() {
        Executor executor = new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };
        ReviewTaskWorker worker = Mockito.mock(ReviewTaskWorker.class);
        ReviewTaskDispatcher dispatcher = new ReviewTaskDispatcher(executor, worker);
        TransactionTemplate transactionTemplate = new TransactionTemplate(buildTransactionManager());

        transactionTemplate.execute(status -> {
            dispatcher.dispatch(19L);
            verify(worker, never()).process(19L);
            return null;
        });

        verify(worker).process(19L);
    }

    @Test
    void shouldDispatchImmediatelyWithoutTransaction() {
        Executor executor = new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };
        ReviewTaskWorker worker = Mockito.mock(ReviewTaskWorker.class);
        ReviewTaskDispatcher dispatcher = new ReviewTaskDispatcher(executor, worker);

        dispatcher.dispatch(23L);

        verify(worker).process(23L);
    }

    private DataSourceTransactionManager buildTransactionManager() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:review-task-dispatcher-test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return new DataSourceTransactionManager(dataSource);
    }
}
