module com.udacity.catpoint {
    requires com.udacity.catpoint.image;
    requires com.miglayout.swing;
    requires java.desktop;
    requires java.prefs;
    requires com.google.common;
    requires com.google.gson;
    requires java.sql;
    requires org.slf4j;
    opens com.udacity.catpoint.data to com.google.gson;
}