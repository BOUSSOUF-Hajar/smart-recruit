package com.smartrecruit.support;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestComponent;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Compte les requêtes SQL préparées pendant un test, toutes technologies confondues (Hibernate,
 * JdbcClient) : la source de données est enveloppée dans un proxy qui compte les appels à
 * {@code prepareStatement}, {@code prepareCall} et {@code createStatement}.
 * <p>
 * À importer seulement dans les tests qui en ont besoin : il change la source de données du contexte.
 */
@TestComponent
public class QueryCounter implements BeanPostProcessor {

    private static final Set<String> STATEMENTS = Set.of("prepareStatement", "prepareCall", "createStatement");

    private final AtomicInteger count = new AtomicInteger();
    private final List<String> statements = new CopyOnWriteArrayList<>();

    public void reset() {
        count.set(0);
        statements.clear();
    }

    public int count() {
        return count.get();
    }

    /** Requêtes comptées depuis le dernier {@link #reset()}, pour un message d'échec lisible. */
    public List<String> statements() {
        return List.copyOf(statements);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource dataSource && !Proxy.isProxyClass(bean.getClass())) {
            return proxy(DataSource.class, dataSource, (target, method, args) -> {
                Object result = invoke(method, target, args);
                return result instanceof Connection connection ? countingConnection(connection) : result;
            });
        }
        return bean;
    }

    private Connection countingConnection(Connection connection) {
        return proxy(Connection.class, connection, (target, method, args) -> {
            if (STATEMENTS.contains(method.getName())) {
                count.incrementAndGet();
                statements.add(args != null && args.length > 0 && args[0] instanceof String sql ? sql : method.getName());
            }
            return invoke(method, target, args);
        });
    }

    private interface Handler<T> {
        Object handle(T target, Method method, Object[] args) throws Throwable;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, T target, Handler<T> handler) {
        InvocationHandler invocation = (proxy, method, args) -> handler.handle(target, method, args);
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, invocation);
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
