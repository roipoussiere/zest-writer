package com.zestedesavoir.zestwriter.plugins;


import com.zestedesavoir.zestwriter.MainApp;
import com.zestedesavoir.zestwriter.view.MdConvertController;

import java.util.ArrayList;
import java.util.Collections;

abstract public class ZwPlugin{
    private ArrayList<Class> listener = new ArrayList<>();
    protected MainApp mainApp;
    private MdConvertController editor;

    protected ZwPlugin getZwPlugin(){
        return this;
    }

    abstract public ZwPlugin onDefine();

    abstract public void onEnable(MainApp mainApp);

    abstract public void onDisable();

    abstract public ZwPluginVersion getVersion();

    abstract public ArrayList<Class> getListener();

    protected MainApp getMainApp(){
        return mainApp;
    }

    protected MdConvertController getEditor(){
        return editor;
    }

    public void setEditor(MdConvertController editor){
        this.editor = editor;
    }
}
