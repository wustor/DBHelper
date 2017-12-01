package com.wustor.helper;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.wustor.helper.core.DBUtil;


public class DatabaseHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "fatchao.db";
    public static final int DB_VERSION = 1;
    private Class<?> clz;

    public DatabaseHelper(Context context, Class<?> clz) {
        super(context, DB_NAME, null, DB_VERSION);
        this.clz = clz;

    }

    public DatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        DBUtil.createTable(db, clz);//创建的扫描表
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        DBUtil.dropTable(db, clz);
    }

}