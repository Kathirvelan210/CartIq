package com.smartcart.repository;

import com.smartcart.exception.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Repository
public class Db {
    private final JdbcTemplate jdbc;
    public Db(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private Object[] args(Object... args){return Arrays.stream(args).map(v->v instanceof Instant i?Timestamp.from(i):v).toArray();}
    public List<Map<String,Object>> rows(String sql,Object... values){return jdbc.queryForList(sql,args(values));}
    public Optional<Map<String,Object>> maybe(String sql,Object... values){return rows(sql,values).stream().findFirst();}
    public Map<String,Object> one(String sql,Object... values){return maybe(sql,values).orElseThrow(ApiException::missing);}
    public int run(String sql,Object... values){return jdbc.update(sql,args(values));}
    public long count(String sql,Object... values){Long n=jdbc.queryForObject(sql,Long.class,args(values));return n==null?0:n;}
    public void insert(String table,Map<String,Object> fields){
        // Table and column names are code-owned constants; every value is bound.
        String columns=String.join(",",fields.keySet());
        run("INSERT INTO "+table+" ("+columns+") VALUES ("+String.join(",",Collections.nCopies(fields.size(),"?"))+")",fields.values().toArray());
    }
    public static Map<String,Object> fields(Object... pairs){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;}
    public static String id(){return UUID.randomUUID().toString();}
    public static String text(Map<String,Object> r,String key){Object v=r.get(key);return v==null?null:v.toString();}
    public static Long number(Map<String,Object> r,String key){Object v=r.get(key);return v==null?null:((Number)v).longValue();}
    public static long num(Map<String,Object> r,String key){Long v=number(r,key);return v==null?0:v;}
    public static boolean bool(Map<String,Object> r,String key){Object v=r.get(key);return v instanceof Boolean b?b:v instanceof Number n&&n.intValue()!=0;}
    public static BigDecimal decimal(Map<String,Object> r,String key){Object v=r.get(key);return v instanceof BigDecimal b?b:new BigDecimal(v.toString());}
    public static Instant time(Map<String,Object> r,String key){Object v=r.get(key);return v instanceof Timestamp t?t.toInstant():v instanceof java.time.LocalDateTime d?d.toInstant(java.time.ZoneOffset.UTC):(Instant)v;}
}
