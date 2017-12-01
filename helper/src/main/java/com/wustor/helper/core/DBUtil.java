package com.wustor.helper.core;

import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.wustor.helper.annotation.Column;
import com.wustor.helper.annotation.Table;
import com.wustor.helper.util.TextUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;

public class DBUtil {

    public static final String PK1 = "pk1";
    public static final String PK2 = "pk2";

    public static String getTableName(Class<?> clz) {
        if (clz.isAnnotationPresent(Table.class)) {
            String name = clz.getAnnotation(Table.class).name();
            if (TextUtil.isValidate(name)) {
                return name;
            } else {
                return clz.getSimpleName().toLowerCase();
            }
        }
        throw new IllegalArgumentException("the class " + clz.getSimpleName() + " can't map to the table");
    }

    public static String getDropTableStmt(Class<?> clz) {
        // db.execSQL("drop table if exists developer");
        return "drop table if exists " + getTableName(clz);
    }

    // "create table if not exists company (id TEXT primary key NOT NULL , name TEXT, age TEXT, company TEXT, skills TEXT)"
    // "create table if not exists company_developers (pk1 TEXT, pk2 TEXT)"

    private static ArrayList<String> getCreateTableStmt(Class<?> clz) {
        StringBuilder mColumnStmts = new StringBuilder();
        ArrayList<String> stmts = new ArrayList<String>();
        if (clz.isAnnotationPresent(Table.class)) {
            Field[] fields = clz.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                if (fields[i].isAnnotationPresent(Column.class)) {
                    if (fields[i].getAnnotation(Column.class).type() == Column.ColumnType.TMANY) {
                        stmts.add("create table if not exists " + getAssociationTableName(clz, fields[i].getName()) + "(" + PK1 + " TEXT, " + PK2
                                + " TEXT)");
                    }
                    mColumnStmts.append(getOneColumnStmt(fields[i]));
                    mColumnStmts.append(",");
                }
            }
            if (mColumnStmts.length() > 0) {
                mColumnStmts.delete(mColumnStmts.length() - 2, mColumnStmts.length());
            }
        }
        stmts.add("create table if not exists " + getTableName(clz) + " (" + mColumnStmts + ")");
        return stmts;
    }

    public static String getAssociationTableName(Class<?> clz, String association) {
        return getTableName(clz) + "_" + association;
    }

    public static String getIDColumnName(Class<?> clz) {
        if (clz.isAnnotationPresent(Table.class)) {
            Field[] fields = clz.getDeclaredFields();
            Column column = null;
            for (Field field : fields) {
                if (field.isAnnotationPresent(Column.class)) {
                    column = field.getAnnotation(Column.class);
                    if (column.id()) {
                        String id = column.name();
                        if (!TextUtil.isValidate(id)) {
                            id = field.getName();
                        }
                        return id;
                    }
                }
            }
        }
        return null;
    }

    public static String getOneColumnStmt(Field field) {
        if (field.isAnnotationPresent(Column.class)) {
            Column column = field.getAnnotation(Column.class);
            String name = column.name();
            if (!TextUtil.isValidate(name)) {
                name = "[" + field.getName() + "]";
            } else {
                name = "[" + name + "]";
            }
            String type = null;
            Class<?> clz = field.getType();
            if (clz == String.class) {
                type = " TEXT ";
            } else if (clz == int.class || clz == Integer.class) {
                type = " integer ";
            } else {
                Column.ColumnType columnType = column.type();
                if (columnType == Column.ColumnType.TONE) {
                    type = " TEXT ";
                } else if (columnType == Column.ColumnType.SERIALIZABLE) {
                    type = " BLOB ";
                } else if (columnType == Column.ColumnType.TMANY) {
                    // do nothing
                }
                // TODO TMANY
            }
            name += type;
            // TODO not null unique
            if (column.id()) {
                name += " primary key ";
            }
            return name;
        }
        return "";
    }

    public static void dropTable(SQLiteDatabase db, Class<?> clz) throws SQLException {
//		TODO don't forget to drop the association table
        db.execSQL(getDropTableStmt(clz));
    }

    public static void createTable(SQLiteDatabase db, Class<?> clz) throws SQLException {
        ArrayList<String> stmts = getCreateTableStmt(clz);
        for (String stmt : stmts) {
            db.execSQL(stmt);
        }
//		db.execSQL(getCreateTableStmt(clz));
    }

    public static String getColumnName(Field field) {
        Column column = field.getAnnotation(Column.class);
        String name = column.name();
        if (!TextUtil.isValidate(name)) {
            name = field.getName();
        }
        return name;
    }

    public static ArrayList<Field> getForeignFields(Field[] mColumnFields) {
        Column column = null;
        ArrayList<Field> fields = new ArrayList<Field>();
        for (Field field : mColumnFields) {
            if (field.isAnnotationPresent(Column.class)) {
                column = field.getAnnotation(Column.class);
                if (column.type() == Column.ColumnType.TMANY) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

}
