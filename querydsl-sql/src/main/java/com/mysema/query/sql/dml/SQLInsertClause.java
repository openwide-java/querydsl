/*
 * Copyright (c) 2010 Mysema Ltd.
 * All rights reserved.
 *
 */
package com.mysema.query.sql.dml;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mysema.commons.lang.Assert;
import com.mysema.query.DefaultQueryMetadata;
import com.mysema.query.JoinType;
import com.mysema.query.QueryException;
import com.mysema.query.QueryFlag;
import com.mysema.query.QueryMetadata;
import com.mysema.query.QueryFlag.Position;
import com.mysema.query.dml.InsertClause;
import com.mysema.query.sql.AbstractSQLSubQuery;
import com.mysema.query.sql.Configuration;
import com.mysema.query.sql.RelationalPath;
import com.mysema.query.sql.SQLSerializer;
import com.mysema.query.sql.SQLTemplates;
import com.mysema.query.types.ConstantImpl;
import com.mysema.query.types.Expression;
import com.mysema.query.types.NullExpression;
import com.mysema.query.types.Path;
import com.mysema.query.types.SubQueryExpression;
import com.mysema.query.types.expr.Param;
import com.mysema.util.ResultSetAdapter;

/**
 * SQLInsertClause defines an INSERT INTO clause
 *
 * @author tiwe
 *
 */
public class SQLInsertClause extends AbstractSQLClause implements InsertClause<SQLInsertClause> {

    private static final Logger logger = LoggerFactory.getLogger(SQLInsertClause.class);

    private final Connection connection;

    private final RelationalPath<?> entity;

    private final QueryMetadata metadata = new DefaultQueryMetadata();

    @Nullable
    private SubQueryExpression<?> subQuery;
    
    @Nullable
    private AbstractSQLSubQuery<?> subQueryBuilder;

    private final List<SQLInsertBatch> batches = new ArrayList<SQLInsertBatch>();

    private final List<Path<?>> columns = new ArrayList<Path<?>>();

    private final List<Expression<?>> values = new ArrayList<Expression<?>>();

    private transient String queryString;

    public SQLInsertClause(Connection connection, SQLTemplates templates, RelationalPath<?> entity) {
        this(connection, new Configuration(templates), entity);
    }
    
    public SQLInsertClause(Connection connection, SQLTemplates templates, RelationalPath<?> entity, AbstractSQLSubQuery<?> subQuery) {
        this(connection, new Configuration(templates), entity);
        this.subQueryBuilder = subQuery;
    }

    public SQLInsertClause(Connection connection, Configuration configuration, RelationalPath<?> entity, AbstractSQLSubQuery<?> subQuery) {
        this(connection, configuration, entity);
        this.subQueryBuilder = subQuery;
    }
    
    public SQLInsertClause(Connection connection, Configuration configuration, RelationalPath<?> entity) {
        super(configuration);
        this.connection = Assert.notNull(connection,"connection");
        this.entity = Assert.notNull(entity,"entity");
        metadata.addJoin(JoinType.DEFAULT, entity);
    }

    /**
     * Add the given String literal at the given position as a query flag
     *
     * @param position
     * @param flag
     * @return
     */
    public SQLInsertClause addFlag(Position position, String flag){
        metadata.addFlag(new QueryFlag(position, flag));
        return this;
    }

    /**
     * Add the current state of bindings as a batch item
     *
     * @return
     */
    public SQLInsertClause addBatch() {
        if (subQueryBuilder != null) {
            subQuery = subQueryBuilder.list(values.toArray(new Expression[values.size()])); 
            values.clear();
        }        
        batches.add(new SQLInsertBatch(columns, values, subQuery));
        columns.clear();
        values.clear();
        subQuery = null;
        return this;
    }

    protected void close(PreparedStatement stmt) {
        try {
            stmt.close();
        } catch (SQLException e) {
            throw new QueryException(e);
        }
    }

    protected void close(ResultSet rs){
        try {
            rs.close();
        } catch (SQLException e) {
            throw new QueryException(e);
        }
    }

    @Override
    public SQLInsertClause columns(Path<?>... columns) {
        this.columns.addAll(Arrays.asList(columns));
        return this;
    }

    /**
     * Execute the clause and return the generated key with the type of the given path
     * If no rows were created, null is returned, otherwise the key of the first row is returned.
     *
     * @param <T>
     * @param path
     * @return
     */
    @Nullable
    public <T> T executeWithKey(Path<T> path){
        ResultSet rs = executeWithKeys();
        try{
            if (rs.next()){
                return configuration.get(rs, path, 1, path.getType());
            }else{
                return null;
            }
        } catch (SQLException e) {
            throw new QueryException(e.getMessage(), e);
        }finally{
            close(rs);
        }
    }

    /**
     * Execute the clause and return the generated keys with the type of the given path.
     * If now rows were created and empty list is returned.
     *
     * @param <T>
     * @param path
     * @return
     */
    public <T> List<T> executeWithKeys(Path<T> path){
        ResultSet rs = executeWithKeys();
        try{
            List<T> rv = new ArrayList<T>();
            while (rs.next()){
                rv.add(configuration.get(rs, path, 1, path.getType()));
            }
            return rv;
        } catch (SQLException e) {
            throw new QueryException(e.getMessage(), e);
        }finally{
            close(rs);
        }
    }

    private PreparedStatement createStatement(boolean withKeys) throws SQLException{
        SQLSerializer serializer = new SQLSerializer(configuration.getTemplates(), true);
        if (subQueryBuilder != null) {
            subQuery = subQueryBuilder.list(values.toArray(new Expression[values.size()])); 
            values.clear();
        } 
        PreparedStatement stmt = null;
        if (batches.isEmpty()){
            serializer.serializeForInsert(metadata, entity, columns, values, subQuery);
            stmt = prepareStatementAndSetParameters(serializer, withKeys);
        }else{
            serializer.serializeForInsert(metadata, entity, batches.get(0).getColumns(), batches.get(0).getValues(), batches.get(0).getSubQuery());
            stmt = prepareStatementAndSetParameters(serializer, withKeys);

            // add first batch
            stmt.addBatch();

            // add other batches
            for (int i = 1; i < batches.size(); i++){
                SQLInsertBatch batch = batches.get(i);
                serializer = new SQLSerializer(configuration.getTemplates(), true, true);
                serializer.serializeForInsert(metadata, entity, batch.getColumns(), batch.getValues(), batch.getSubQuery());
                setParameters(stmt, serializer.getConstants(), serializer.getConstantPaths(), Collections.<Param<?>,Object>emptyMap());
                stmt.addBatch();
            }
        }
        return stmt;
    }

    private PreparedStatement prepareStatementAndSetParameters(SQLSerializer serializer, boolean withKeys) throws SQLException {
        queryString = serializer.toString();
        logger.debug(queryString);
        PreparedStatement stmt;
        if (withKeys){
            stmt = connection.prepareStatement(queryString, Statement.RETURN_GENERATED_KEYS);
        }else{
            stmt = connection.prepareStatement(queryString);
        }
        setParameters(stmt, serializer.getConstants(), serializer.getConstantPaths(), Collections.<Param<?>,Object>emptyMap());
        return stmt;
    }

    /**
     * Execute the clause and return the generated keys as a ResultSet
     *
     * @return
     */
    public ResultSet executeWithKeys(){
        try {
            final PreparedStatement stmt = createStatement(true);
            if (batches.isEmpty()){
                stmt.executeUpdate();
            }else{
                stmt.executeBatch();
            }
            ResultSet rs = stmt.getGeneratedKeys();
            return new ResultSetAdapter(rs){
                @Override
                public void close() throws SQLException {
                    try {
                        super.close();
                    } finally {
                        stmt.close();
                    }
                }
            };
        } catch (SQLException e) {
            throw new QueryException("Caught " + e.getClass().getSimpleName() + " for " + queryString, e);
        }
    }

    @Override
    public long execute() {
        PreparedStatement stmt = null;
        try {
            stmt = createStatement(false);
            if (batches.isEmpty()){
                return stmt.executeUpdate();
            }else{
                long rv = 0;
                for (int i : stmt.executeBatch()){
                    rv += i;
                }
                return rv;
            }
        } catch (SQLException e) {
            throw new QueryException("Caught " + e.getClass().getSimpleName() + " for " + queryString, e);
        } finally {
            if (stmt != null) {
                close(stmt);
            }
        }
    }

    @Override
    public SQLInsertClause select(SubQueryExpression<?> sq) {
        subQuery = sq;
        return this;
    }

    @Override
    public <T> SQLInsertClause set(Path<T> path, T value) {
        columns.add(path);
        if (value instanceof Expression<?>){
            values.add((Expression<?>)value);
        }else if (value != null){
            values.add(new ConstantImpl<T>(value));
        }else{
            values.add(new NullExpression<T>(path.getType()));
        }
        return this;
    }

    @Override
    public <T> SQLInsertClause set(Path<T> path, Expression<? extends T> expression) {
        columns.add(path);
        values.add(expression);
        return this;
    }

    @Override
    public <T> SQLInsertClause setNull(Path<T> path){
        columns.add(path);
        values.add(new NullExpression<T>(path.getType()));
        return this;
    }

    @Override
    public SQLInsertClause values(Object... v) {
        for (Object value : v) {
            if (value instanceof Expression<?>) {
                values.add((Expression<?>) value);
            } else if (value != null){
                values.add(new ConstantImpl<Object>(value));
            }else{
                values.add(NullExpression.DEFAULT);
            }
        }
        return this;
    }

    @Override
    public String toString(){
        SQLSerializer serializer = new SQLSerializer(configuration.getTemplates(), true);
        serializer.serializeForInsert(metadata, entity, columns, values, subQuery);
        return serializer.toString();
    }

    /**
     * Populate the INSERT clause with the properties of the given bean.
     * The properties need to match the fields of the clause's entity instance.
     *
     * @param bean
     * @return
     */
    @SuppressWarnings("unchecked")
    public SQLInsertClause populate(Object bean) {
        try {
            Class<?> beanClass = bean.getClass();
            for (Field beanField : beanClass.getDeclaredFields()) {
                if (!Modifier.isStatic(beanField.getModifiers())) {
                    Field field = entity.getClass().getDeclaredField(beanField.getName());
                    field.setAccessible(true);
                    Path path = (Path<?>) field.get(entity);
                    beanField.setAccessible(true);
                    Object propertyValue = beanField.get(bean);
                    if (propertyValue != null) {
                        set(path, propertyValue);
                    }     
                }
            }
//            BeanMap map = new BeanMap(bean);
//            for (Map.Entry entry : map.entrySet()){
//                String property = entry.getKey().toString();
//                if (!property.equals("class")){
//                    Field field = entity.getClass().getDeclaredField(property);
//                    field.setAccessible(true);
//                    Path path = (Path<?>) field.get(entity);
//                    if (entry.getValue() != null) {
//                        set(path, entry.getValue());    
//                    }                    
//                }
//            }
            return this;
        } catch (SecurityException e) {
            throw new QueryException(e);
        } catch (IllegalAccessException e) {
            throw new QueryException(e);
        } catch (NoSuchFieldException e) {
            throw new QueryException(e);
        }
    }

}
