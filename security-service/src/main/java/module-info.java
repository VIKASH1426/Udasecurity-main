module com.udacity.catpoint {


    requires com.udacity.catpoint.image;
    requires java.desktop;
    requires com.google.gson;
    requires com.google.common;
    requires miglayout.swing;
    requires org.slf4j;
    requires java.prefs;

    opens com.udacity.catpoint.data to com.google.gson;
}