package com.wustor.helper.core;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.cmyy.chuangmei.db.DBManager;
import com.cmyy.chuangmei.db.annotation.Column;
import com.cmyy.chuangmei.db.annotation.Table;
import com.cmyy.chuangmei.db.util.SerializeUtil;
import com.cmyy.chuangmei.db.util.TextUtil;
import com.cmyy.chuangmei.db.util.Trace;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;

public class BaseDao<T> {
    private SQLiteDatabase mDatabase;
    private Class<T> clz;
    private String mTableName;
    private String mIdName;
    private Field[] mColumnFields;
    private Field mIdField;
    private Context context;
    private ArrayList<Field> mForeignFields;

    public BaseDao(Context context, Class<T> clz, SQLiteDatabase db) {
        this.clz = clz;
        this.mDatabase = db;
        this.context = context;
        try {
            mTableName = DBUtil.getTableName(clz);
            mIdName = DBUtil.getIDColumnName(clz);
            mIdField = clz.getDeclaredField(mIdName);
            mIdField.setAccessible(true);
            mColumnFields = clz.getDeclaredFields();
            mForeignFields = DBUtil.getForeignFields(mColumnFields);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    public void beginTransaction() {
        mDatabase.beginTransaction();
    }

    public void endTransaction() {
        endTransaction(true);
    }

    public void endTransaction(boolean successful) {
        if (successful) {
            mDatabase.setTransactionSuccessful();
        }
        mDatabase.endTransaction();
    }

    public <T> void newOrUpdate(T t) {
        ContentValues values = new ContentValues();
        try {
            String idValue = (String) mIdField.get(t);
            for (Field field : mColumnFields) {
                if (field.isAnnotationPresent(Column.class)) {
                    field.setAccessible(true);
                    Class<?> clz = field.getType();
                    if (clz == String.class) {
                        Object value = field.get(t);
                        if (value != null) {
                            values.put(DBUtil.getColumnName(field), value.toString());
                        }
                    } else if (clz == int.class || clz == Integer.class) {
                        values.put(DBUtil.getColumnName(field), field.getInt(t));
                    } else {
                        Column column = field.getAnnotation(Column.class);
                        Column.ColumnType type = column.type();
                        if (!TextUtil.isValidate(type.name())) {
                            throw new IllegalArgumentException("you should set type to the special column:" + t.getClass().getSimpleName() + "."
                                    + field.getName());
                        }
                        if (type == Column.ColumnType.SERIALIZABLE) {
                            byte[] value = SerializeUtil.serialize(field.get(t));
                            values.put(DBUtil.getColumnName(field), value);
                        } else if (type == Column.ColumnType.TONE) {
                            Object tone = field.get(t);
                            if (tone == null) {
                                continue;
                            }
                            if (column.autofresh()) {
                                // TODO save object to related table
                                DBManager.getInstance().getDao(tone.getClass()).newOrUpdate(tone);
                            }
                            if (tone.getClass().isAnnotationPresent(Table.class)) {
                                String idName = DBUtil.getIDColumnName(tone.getClass());
                                Field toneIdField = tone.getClass().getDeclaredField(idName);
                                toneIdField.setAccessible(true);
                                values.put(DBUtil.getColumnName(field), toneIdField.get(tone).toString());
                            }
                        } else if (type == Column.ColumnType.TMANY) {
                            List<Object> tmany = (List<Object>) field.get(t);
                            if (tmany == null) {
                                continue;
                            }
                            String associationTable = DBUtil.getAssociationTableName(t.getClass(), field.getName());
                            delete(associationTable, "pk1=?", new String[]{idValue});
                            if (tmany != null) {
                                ContentValues associationValues = new ContentValues();
                                for (Object object : tmany) {
                                    if (column.autofresh()) {
                                        DBManager.getInstance().getDao(object.getClass()).newOrUpdate(object);
                                    }
                                    associationValues.clear();
                                    associationValues.put(DBUtil.PK1, idValue);// company
                                    // id
                                    String idName = DBUtil.getIDColumnName(object.getClass());
                                    Field tmanyIdField = object.getClass().getDeclaredField(idName);
                                    tmanyIdField.setAccessible(true);
                                    String value = tmanyIdField.get(object).toString();
                                    associationValues.put(DBUtil.PK2, value); // developer
                                    // id
                                    newOrUpdate(associationTable, associationValues);
                                    // 1,2 3,-1 2,3
                                }
                            }
                        }

                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        newOrUpdate(mTableName, values);
    }

    public void newOrUpdate(String tableName, ContentValues values) {
        mDatabase.replace(tableName, null, values);
    }

    public void delete(String id) {
        try {
            delete(DBUtil.getTableName(clz), mIdName + "=?", new String[]{id});
            // delete related association data
            for (Field field : mForeignFields) {
                delete(DBUtil.getAssociationTableName(clz, field.getName()), "pk1=?", new String[]{id});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public <T> void delete(T t) {
        try {
            String id = (String) mIdField.get(t);
            delete(id);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void delete(String tableName, String where, String[] args) {
        mDatabase.delete(tableName, where, args);
    }

    public Cursor rawQuery(String tableName, String where, String[] args) {
        Cursor cursor = mDatabase.rawQuery("select * from " + tableName + " where " + where, args);
        return cursor;
    }

    public T queryById(String id) {
        Cursor cursor = rawQuery(mTableName, mIdName + "=?", new String[]{id});
        T t = null;
        if (cursor.moveToNext()) {
            try {
                t = clz.newInstance();
                for (Field field : mColumnFields) {
                    if (field.isAnnotationPresent(Column.class)) {
                        field.setAccessible(true);
                        Class<?> columnType = field.getType();
                        if (columnType == Integer.class || columnType == int.class) {
                            field.setInt(t, cursor.getInt(cursor.getColumnIndex(DBUtil.getColumnName(field))));
                        } else if (columnType == String.class) {
                            field.set(t, cursor.getString(cursor.getColumnIndex(DBUtil.getColumnName(field))));
                        } else {
                            Column column = field.getAnnotation(Column.class);
                            Column.ColumnType type = column.type();
                            if (!TextUtil.isValidate(type.name())) {
                                throw new IllegalArgumentException("you should set type to the special column:" + t.getClass().getSimpleName() + "."
                                        + field.getName());
                            }
                            if (type == Column.ColumnType.SERIALIZABLE) {
                                field.set(t, SerializeUtil.deserialize(cursor.getBlob(cursor.getColumnIndex(DBUtil.getColumnName(field)))));
                                // field.set(t,
                                // JsonUtil.fromJson(cursor.getString(cursor.getColumnIndex(DBUtil.getColumnName(field))),field.getType()));
                            } else if (type == Column.ColumnType.TONE) {
                                String toneId = cursor.getString(cursor.getColumnIndex(DBUtil.getColumnName(field)));
                                if (!TextUtil.isValidate(toneId)) {
                                    continue;
                                }
                                Trace.d("query -- tone.id:" + toneId);
                                Object tone = null;
                                if (column.autofresh()) {
                                    tone = DBManager.getInstance().getDao(field.getType()).queryById(toneId);
                                } else {
                                    tone = field.getType().newInstance();
                                    if (field.getType().isAnnotationPresent(Table.class)) {
                                        String idName = DBUtil.getIDColumnName(field.getType());
                                        Field idField = field.getType().getDeclaredField(idName);
                                        idField.setAccessible(true);
                                        idField.set(tone, toneId);
                                    }
                                }
                                field.set(t, tone);
                            } else if (type == Column.ColumnType.TMANY) {
//								TODO
                                Class relatedClass = (Class) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                                Cursor tmanyCursor = mDatabase.rawQuery("select * from " + DBUtil.getAssociationTableName(clz, field.getName()) + " where " + DBUtil.PK1 + "=?", new String[]{id});
                                ArrayList list = new ArrayList();
                                String tmanyId = null;
                                Object tmany = null;
                                while (tmanyCursor.moveToNext()) {
                                    tmanyId = tmanyCursor.getString(tmanyCursor.getColumnIndex(DBUtil.PK2));
                                    if (column.autofresh()) {
                                        tmany = DBManager.getInstance().getDao(relatedClass).queryById(tmanyId);
                                    } else {
                                        tmany = relatedClass.newInstance();
                                        String idName = DBUtil.getIDColumnName(relatedClass);
                                        Field idField = relatedClass.getDeclaredField(idName);
                                        idField.setAccessible(true);
                                        idField.set(tmany, tmanyId);
                                    }
                                    list.add(tmany);
                                }
                                if (!TextUtil.isValidate(list)) {
                                    continue;
                                }
                                field.set(t, list);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return t;
    }

}
