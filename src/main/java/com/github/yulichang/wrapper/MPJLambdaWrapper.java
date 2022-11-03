package com.github.yulichang.wrapper;

import com.baomidou.mybatisplus.core.conditions.SharedString;
import com.baomidou.mybatisplus.core.conditions.segments.MergeSegments;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.ArrayUtils;
import com.baomidou.mybatisplus.core.toolkit.Assert;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.github.yulichang.toolkit.*;
import com.github.yulichang.toolkit.support.ColumnCache;
import com.github.yulichang.toolkit.support.SelectColumn;
import com.github.yulichang.wrapper.enums.BaseFuncEnum;
import com.github.yulichang.wrapper.interfaces.LambdaJoin;
import com.github.yulichang.wrapper.interfaces.Query;
import com.github.yulichang.wrapper.interfaces.on.OnFunction;
import lombok.Getter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 参考 {@link com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper}
 * Lambda 语法使用 Wrapper
 * <p>
 * 推荐使用 MPJWrappers.<UserDO>lambdaJoin();构造
 *
 * @author yulichang
 * @see MPJWrappers
 */
@SuppressWarnings("unused")
public class MPJLambdaWrapper<T> extends MPJAbstractLambdaWrapper<T, MPJLambdaWrapper<T>>
        implements Query<MPJLambdaWrapper<T>>, LambdaJoin<MPJLambdaWrapper<T>, T> {

    /**
     * 查询表
     */
    private final SharedString from = new SharedString();
    /**
     * 主表别名
     */
    private final SharedString alias = new SharedString(Constant.TABLE_ALIAS);
    /**
     * 查询的字段
     */
    @Getter
    private final List<SelectColumn> selectColumns = new UniqueList<>();
    /**
     * ON sql wrapper集合
     */
    private final List<MPJLambdaWrapper<?>> onWrappers = new ArrayList<>();
    /**
     * 查询字段 sql
     */
    private SharedString sqlSelect = new SharedString();
    /**
     * 是否 select distinct
     */
    private boolean selectDistinct = false;
    /**
     * 是否构建resultMap
     */
    @Getter
    private final boolean resultMap = false;
    /**
     * 表序号
     */
    private int tableIndex = 1;
    /**
     * 连表关键字 on 条件 func 使用
     */
    @Getter
    private String keyWord;

    /**
     * 连表实体类 on 条件 func 使用
     */
    @Getter
    private Class<?> joinClass;

    /**
     * 不建议直接 new 该实例，使用 MPJWrappers.<UserDO>lambdaQuery()
     */
    public MPJLambdaWrapper() {
        super.initNeed();
    }


    /**
     * 不建议直接 new 该实例，使用 MPJWrappers.<UserDO>lambdaQuery()
     */
    MPJLambdaWrapper(T entity, Class<T> entityClass, SharedString sqlSelect, AtomicInteger paramNameSeq,
                     Map<String, Object> paramNameValuePairs, MergeSegments mergeSegments,
                     SharedString lastSql, SharedString sqlComment, SharedString sqlFirst,
                     Map<Class<?>, Integer> subTable, String keyWord, Class<?> joinClass) {
        super.setEntity(entity);
        super.setEntityClass(entityClass);
        this.paramNameSeq = paramNameSeq;
        this.paramNameValuePairs = paramNameValuePairs;
        this.expression = mergeSegments;
        this.sqlSelect = sqlSelect;
        this.lastSql = lastSql;
        this.sqlComment = sqlComment;
        this.sqlFirst = sqlFirst;
        this.subTable = subTable;
        this.keyWord = keyWord;
        this.joinClass = joinClass;
    }


    /**
     * sql去重
     * select distinct
     */
    public MPJLambdaWrapper<T> distinct() {
        this.selectDistinct = true;
        return typedThis;
    }

    @Override
    @SafeVarargs
    public final <S> MPJLambdaWrapper<T> select(SFunction<S, ?>... columns) {
        if (ArrayUtils.isNotEmpty(columns)) {
            for (SFunction<S, ?> s : columns) {
                ColumnCache cache = getCache(s);
                selectColumns.add(SelectColumn.of(LambdaUtils.getEntityClass(s), cache.getColumn(), cache.getTableFieldInfo(),
                        null, cache.getTableFieldInfo() == null ? cache.getKeyProperty() : cache.getTableFieldInfo().getProperty(), null));
            }
        }
        return typedThis;
    }

    @Override
    public <E> MPJLambdaWrapper<T> select(Class<E> entityClass, Predicate<TableFieldInfo> predicate) {
        TableInfo info = TableInfoHelper.getTableInfo(entityClass);
        Assert.notNull(info, "table can not be find");
        info.getFieldList().stream().filter(predicate).collect(Collectors.toList()).forEach(
                i -> selectColumns.add(SelectColumn.of(entityClass, i.getColumn(), i, null, i.getProperty(), null)));
        return typedThis;
    }

    @Override
    public <E> MPJLambdaWrapper<T> selectAsClass(Class<E> source, Class<?> tag) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(source);
        Assert.notNull(tableInfo, "table can not be find");
        List<Field> tagFields = ReflectionKit.getFieldList(tag);
        tableInfo.getFieldList().forEach(i -> {
            if (tagFields.stream().anyMatch(f -> f.getName().equals(i.getProperty()))) {
                selectColumns.add(SelectColumn.of(source, i.getColumn(), i, null, i.getProperty(), null));
            }
        });
        if (tableInfo.havePK() && tagFields.stream().anyMatch(i -> i.getName().equals(tableInfo.getKeyProperty()))) {
            selectColumns.add(SelectColumn.of(source, tableInfo.getKeyColumn(), null, null, tableInfo.getKeyProperty(), null));
        }
        return typedThis;
    }

    @Override
    public <S> MPJLambdaWrapper<T> selectAs(SFunction<S, ?> column, String alias) {
        ColumnCache cache = getCache(column);
        selectColumns.add(SelectColumn.of(LambdaUtils.getEntityClass(column), cache.getColumn(), cache.getTableFieldInfo(), alias, null, null));
        return typedThis;
    }

    public <S> MPJLambdaWrapper<T> selectFunc(boolean condition, BaseFuncEnum funcEnum, SFunction<S, ?> column, String alias) {
        if (condition) {
            ColumnCache cache = getCache(column);
            selectColumns.add(SelectColumn.of(LambdaUtils.getEntityClass(column), cache.getColumn(), cache.getTableFieldInfo(), alias, alias, funcEnum));
        }
        return typedThis;
    }

    @Override
    public MPJLambdaWrapper<T> selectFunc(boolean condition, BaseFuncEnum funcEnum, Object column, String alias) {
        if (condition) {
            selectColumns.add(SelectColumn.of(null, column.toString(), null, alias, alias, funcEnum));
        }
        return typedThis;
    }

    public final MPJLambdaWrapper<T> selectAll(Class<?> clazz) {
        TableInfo info = TableInfoHelper.getTableInfo(clazz);
        Assert.notNull(info, "table can not be find -> %s", clazz);
        if (info.havePK()) {
            selectColumns.add(SelectColumn.of(clazz, info.getKeyColumn(), null, null, info.getKeyProperty(), null));
        }
        info.getFieldList().forEach(c ->
                selectColumns.add(SelectColumn.of(clazz, c.getColumn(), c, null, c.getProperty(), null)));
        return typedThis;
    }

    /**
     * 查询条件 SQL 片段
     */
    @Override
    public String getSqlSelect() {
        if (StringUtils.isBlank(sqlSelect.getStringValue())) {
            String s = selectColumns.stream().map(i -> {
                String str = Constant.TABLE_ALIAS + getDefault(subTable.get(i.getClazz())) + StringPool.DOT + i.getColumnName();
                return (i.getFuncEnum() == null ? str : String.format(i.getFuncEnum().getSql(), str)) +
                        (StringUtils.isBlank(i.getAlias()) ? StringPool.EMPTY : (Constant.AS + i.getAlias()));
            }).collect(Collectors.joining(StringPool.COMMA));
            sqlSelect.setStringValue(s);
        }
        return sqlSelect.getStringValue();
    }

    /**
     * 获取连表部分语句
     */
    public String getFrom() {
        if (StringUtils.isBlank(from.getStringValue())) {
            StringBuilder value = new StringBuilder();
            for (MPJLambdaWrapper<?> wrapper : onWrappers) {
                String tableName = TableInfoHelper.getTableInfo(wrapper.getJoinClass()).getTableName();
                value.append(wrapper.getKeyWord())
                        .append(tableName)
                        .append(Constant.SPACE_TABLE_ALIAS)
                        .append(subTable.get(wrapper.getJoinClass()))
                        .append(Constant.ON)
                        .append(wrapper.getExpression().getNormal().getSqlSegment());
            }
            from.setStringValue(value.toString());
        }
        return from.getStringValue();
    }

    public String getAlias() {
        return alias.getStringValue();
    }


    public boolean getSelectDistinct() {
        return selectDistinct;
    }

    /**
     * 用于生成嵌套 sql
     * <p>故 sqlSelect 不向下传递</p>
     */
    @Override
    protected MPJLambdaWrapper<T> instance() {
        return instance(null, null);
    }

    protected MPJLambdaWrapper<T> instance(String keyWord, Class<?> joinClass) {
        return new MPJLambdaWrapper<>(getEntity(), getEntityClass(), null, paramNameSeq, paramNameValuePairs,
                new MergeSegments(), SharedString.emptyString(), SharedString.emptyString(), SharedString.emptyString(),
                this.subTable, keyWord, joinClass);
    }

    @Override
    public void clear() {
        super.clear();
        sqlSelect.toNull();
        from.toNull();
        selectColumns.clear();
        subTable.clear();
    }

    @Override
    public <R> MPJLambdaWrapper<T> join(String keyWord, boolean condition, Class<R> clazz, OnFunction function) {
        if (condition) {
            MPJLambdaWrapper<?> apply = function.apply(instance(keyWord, clazz));
            onWrappers.add(apply);
            subTable.put(clazz, tableIndex);
            tableIndex++;
        }
        return typedThis;
    }
}
