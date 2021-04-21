package com.example.sound.pipedpiperapnom;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DBConnect extends AsyncTask<String, Void, String> {
    private static final String url = "jdbc:mysql://54.166.155.185/attendance_book";
    private static final String user = "root";
    private static final String pass = "";
    private Context context;
    private int StudentID;
    private String classLoc;
    private String attendDate;
    private String attendTime;

    public void setStudentID(int studentID) {
        StudentID = studentID;
    }

    public void setClassLoc(String classLoc) {
        this.classLoc = classLoc;
    }

    public void setAttendDate(String attendDate) {
        this.attendDate = attendDate;
    }

    public void setAttendTime(String attendTime) {
        this.attendTime = attendTime;
    }

    public DBConnect(Context context){
        this.context = context;

    }

    protected void OnPreExecute(){
        super.onPreExecute();
       // Toast.makeText(context,"please wait...", Toast.LENGTH_SHORT).show();
    }



    @Override
    protected String doInBackground(String... strings) {
        try{
            Class.forName("com.mysql.jdbc.Driver");
            Connection con = DriverManager.getConnection(url, user,pass);

            String result = "Database Connection Successfull\n";
            Statement st = con.createStatement();
            String queryStatement = String.format("insert into dataStructure \"" +
                    "(studentID, classLoc, attendDate, attendTime)\"" +
                    "values( %d, %s, %s, %s);", StudentID, classLoc, attendDate, attendTime);
            ResultSet rs  = st.executeQuery(queryStatement);
            con.close();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "success";
    }
    @Override
    protected void onPostExecute(String result){
       // Toast.makeText(this.context,"mysql Upload Success",Toast.LENGTH_SHORT);
        Log.d("DBConnect","mysql Upload Success" );


    }


}
