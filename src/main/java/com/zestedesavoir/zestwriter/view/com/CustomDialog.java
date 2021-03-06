package com.zestedesavoir.zestwriter.view.com;

import com.zestedesavoir.zestwriter.MainApp;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class CustomDialog<T> extends Dialog<T> {

    public CustomDialog() {
        super();
        FunctionTreeFactory.addTheming(this.getDialogPane());
        initModality(Modality.APPLICATION_MODAL);
        initOwner(MainApp.getPrimaryStage());
        getDialogPane().getChildren().stream().filter(node -> node instanceof Label).forEach(node -> ((Label)node).setMinHeight(Region.USE_PREF_SIZE));
    }
}
