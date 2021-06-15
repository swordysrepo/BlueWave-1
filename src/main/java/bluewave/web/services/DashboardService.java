package bluewave.web.services;
import bluewave.app.Dashboard;
import bluewave.app.DashboardUser;

import javaxt.express.*;
import javaxt.http.servlet.*;
import javaxt.sql.*;
import javaxt.json.*;

import java.util.*;
import java.io.IOException;
import static javaxt.express.WebService.console;


//******************************************************************************
//**  DashboardService
//******************************************************************************
/**
 *   Used to view and manage dashboards
 *
 ******************************************************************************/

public class DashboardService extends WebService {

    private bluewave.web.WebServices ws;
    private String format = "png";


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public DashboardService(bluewave.web.WebServices ws, javaxt.io.Directory web, Database database) throws Exception {
        this.ws = ws;


        super.addClass(bluewave.app.Dashboard.class);
        //super.addClass(bluewave.app.DashboardUser.class);
        //super.addClass(bluewave.app.DashboardGroup.class);


        Connection conn = null;
        try{

          //Find dashboards in the database
            HashSet<String> dashboards = new HashSet<>();
            conn = database.getConnection();
            Recordset rs = new Recordset();
            String tableName = Model.getTableName(new Dashboard());
            rs.open("select class_name from " + tableName, conn);
            while (rs.hasNext()){
                String className = rs.getValue(0).toString();
                int idx = className.lastIndexOf(".");
                if (idx>0) className = className.substring(idx+1);
                dashboards.add(className);
                rs.moveNext();
            }
            rs.close();
            conn.close();


          //Add dashboards as needed
            javaxt.io.Directory dir = new javaxt.io.Directory(web + "app/dashboards/");
            for (javaxt.io.File file : dir.getFiles("*.js")){
                String className = file.getName(false);
                if (!dashboards.contains(className)){
                    Dashboard dashboard = new Dashboard();
                    dashboard.setName(className);
                    dashboard.setClassName(className);
                    dashboard.save();
                }
            }
        }
        catch(Exception e){
            if (conn!=null) conn.close();
            throw e;
        }
    }


  //**************************************************************************
  //** onCreate
  //**************************************************************************
    public void onCreate(Object obj){
        ws.onCreate(obj);
    };


  //**************************************************************************
  //** onUpdate
  //**************************************************************************
    public void onUpdate(Object obj){
        ws.onUpdate(obj);
    };


  //**************************************************************************
  //** onDelete
  //**************************************************************************
    public void onDelete(Object obj){
        ws.onDelete(obj);
    };


  //**************************************************************************
  //** getRecordset
  //**************************************************************************
  /** Used to apply filters before fetching dashboards or executing any CRUD
   *  operations
   */
    protected Recordset getRecordset(ServiceRequest serviceRequest,
        String op, Class c, String sql, Connection conn) throws Exception {

      //Get user associated with the request
        bluewave.app.User user = (bluewave.app.User) serviceRequest.getUser();


      //Prevent read-only users from creating, editing, or deleting dashboards
        if (op.equals("create") || op.equals("update") || op.equals("delete")){
            if (user.getAccessLevel()<3){
                throw new ServletException(403, "Not Authorized");
            }
        }



        SQLEditor where = new SQLEditor(sql);
        if (c.equals(bluewave.app.Dashboard.class)){


            String filter = null;
            if (op.equals("get") || op.equals("list")){
                filter = "id in (" +
                "select dashboard.id " +
                "from APPLICATION.DASHBOARD left join APPLICATION.DASHBOARD_USER " +
                "on APPLICATION.DASHBOARD.ID=APPLICATION.DASHBOARD_USER.dashboard_id " +
                " where user_id=" + user.getID() + " or user_id is null" +
                ")";
            }
            else{
                if (user.getAccessLevel()<5){
                    filter = "id in (" +
                    "select dashboard_id from APPLICATION.DASHBOARD_USER " +
                    "where user_id=" + user.getID() + " and read_only=false" +
                    ")";
                }
            }


            if (filter!=null) where.append(filter);
        }


        sql = where.getSQL();



        Recordset rs = new Recordset();
        if (op.equals("list")) rs.setFetchSize(1000);
        try{
            rs.open(sql, conn);
            return rs;
        }
        catch(Exception e){
            console.log(sql);
            throw e;
        }
    }


  //**************************************************************************
  //** save
  //**************************************************************************
    public ServiceResponse saveDashboard(ServiceRequest request, Database database)
        throws ServletException, IOException {

      //Get user associated with the request
        bluewave.app.User user = (bluewave.app.User) request.getUser();
        if (user.getAccessLevel()<3){
            return new ServiceResponse(403, "Not Authorized");
        }


        Connection conn = null;
        try{
            JSONObject json = new JSONObject(new String(request.getPayload(), "UTF-8"));
            if (json.isEmpty()) throw new Exception("JSON is empty.");


          //Create new instance of the class
            Dashboard dashboard;
            Long id = json.get("id").toLong();
            boolean isNew = false;
            if (id!=null){
                dashboard = new Dashboard(id);


                dashboard.update(json);
            }
            else{
                dashboard = new Dashboard(json);
                isNew = true;
            }



          //Apply filter
            if (!isNew){
                if (!isAuthorized(user, dashboard, database, false))
                    return new ServiceResponse(403, "Not Authorized");
            }


          //Call the save method
            dashboard.save();




          //Create DashboardUser
            if (isNew){
                DashboardUser dashboardUser = new DashboardUser();
                dashboardUser.setUser(user);
                dashboardUser.setDashboard(dashboard);
                dashboardUser.setReadOnly(false);
                dashboardUser.save();
            }


          //Fire event
            if (isNew) onCreate(dashboard); else onUpdate(dashboard);


          //Return response
            return new ServiceResponse(dashboard.getID()+"");
        }
        catch(Exception e){
            if (conn!=null) conn.close();
            return new ServiceResponse(e);
        }
    }


  //**************************************************************************
  //** getUsers
  //**************************************************************************
  /** Returns a list of user IDs associated with the given dashboard
   */
    public ServiceResponse getUsers(ServiceRequest request, Database database)
        throws ServletException, IOException {

      //Get dashboard ID
        Long dashboardID = request.getParameter("dashboardID").toLong();
        if (dashboardID==null) return new ServiceResponse(400, "dashboardID is required");


      //Get user associated with the request
        bluewave.app.User user = (bluewave.app.User) request.getUser();


        Connection conn = null;
        try{

            StringBuilder csv = new StringBuilder();
            boolean foundUser = false;

            conn = database.getConnection();
            Recordset rs = new Recordset();
            rs.open("select user_id, read_only from APPLICATION.DASHBOARD_USER " +
            "where dashboard_id="+dashboardID, conn);
            while (rs.hasNext()){
                long userID = rs.getValue(0).toLong();
                boolean isReadOnly = rs.getValue(0).toBoolean();
                if (userID==user.getID()) foundUser = true;

                if (csv.length()>0) csv.append("\r\n");
                csv.append(userID);
                csv.append(",");
                csv.append(isReadOnly);

                rs.moveNext();
            }
            rs.close();
            conn.close();

            if (!foundUser){
                if (user.getAccessLevel()<5) csv = new StringBuilder();
            }

            return new ServiceResponse(csv.toString());
        }
        catch(Exception e){
            if (conn!=null) conn.close();
            return new ServiceResponse(e);
        }
    }


  //**************************************************************************
  //** saveUser
  //**************************************************************************
  /** Used to grant user access to a given dashboard
   */
    public ServiceResponse saveUser(ServiceRequest request, Database database)
        throws ServletException, IOException {

        Long dashboardID = request.getParameter("dashboardID").toLong();
        if (dashboardID==null) return new ServiceResponse(400, "dashboardID is required");

        Connection conn = null;
        try{
            conn = database.getConnection();

            conn.close();

            return new ServiceResponse(501);
        }
        catch(Exception e){
            if (conn!=null) conn.close();
            return new ServiceResponse(e);
        }
    }


  //**************************************************************************
  //** deleteUser
  //**************************************************************************
  /** Used to remove a user associated with a dashboard
   */
    public ServiceResponse deleteUser(ServiceRequest request, Database database)
        throws ServletException, IOException {

        Long dashboardID = request.getParameter("dashboardID").toLong();
        if (dashboardID==null) return new ServiceResponse(400, "dashboardID is required");

        Connection conn = null;
        try{

            conn = database.getConnection();

            conn.close();

             return new ServiceResponse(501);
        }
        catch(Exception e){
            if (conn!=null) conn.close();
            return new ServiceResponse(e);
        }
    }


  //**************************************************************************
  //** getThumbnail
  //**************************************************************************
  /** Returns a thumbnail associated with a given dashboard
   */
    public ServiceResponse getThumbnail(ServiceRequest request, Database database)
        throws ServletException, IOException {

        try{

            Long id = request.getParameter("id").toLong();
            if (id==null) return new ServiceResponse(400, "Dashboard ID is required");


          //Get dashboard
            Dashboard dashboard = new Dashboard(id);


          //Check if user can access the dashboard
            bluewave.app.User user = (bluewave.app.User) request.getUser();
            if (!isAuthorized(user, dashboard, database, true))
                return new ServiceResponse(403, "Not Authorized");


          //Get thumbnail
            byte[] thumbnail = dashboard.getThumbnail();
            if (thumbnail==null) return new ServiceResponse(404, "Thumbnail does not exist");

            javaxt.io.Image img = new javaxt.io.Image(thumbnail);
            img.setWidth(360);
            byte[] bytes = img.getByteArray(format);
            ServiceResponse response = new ServiceResponse(bytes);
            response.setContentType("image/"+format);
            response.setContentLength(bytes.length);
            return response;
        }
        catch(Exception e){
            return new ServiceResponse(e);
        }
    }


  //**************************************************************************
  //** saveThumbnail
  //**************************************************************************
    public ServiceResponse saveThumbnail(ServiceRequest request, Database database)
        throws ServletException, IOException {


      //Parse form inputs
        Long id = null;
        javaxt.io.Image img = null;
        Iterator<FormInput> it = request.getRequest().getFormInputs();
        while (it.hasNext()){
            FormInput input = it.next();
            String key = input.getName();
            FormValue value = input.getValue();
            if (input.isFile()){
                img = new javaxt.io.Image(value.getInputStream());
            }
            else{
                if (key.equals("id")) id = value.toLong();
            }
        }

        if (id==null) return new ServiceResponse(400, "Dashboard ID is required");
        if (img==null) return new ServiceResponse(400, "Thumbnail is required");


        try{

          //Get dashboard
            Dashboard dashboard = new Dashboard(id);


          //Check if user can edit the dashboard
            bluewave.app.User user = (bluewave.app.User) request.getUser();
            if (!isAuthorized(user, dashboard, database, false))
                return new ServiceResponse(403, "Not Authorized");


          //Save dashboard
            dashboard.setThumbnail(img.getByteArray(format));
            dashboard.save();

            onUpdate(dashboard);
            return new ServiceResponse(200);
        }
        catch(Exception e){
            return new ServiceResponse(e);
        }
    }


  //**************************************************************************
  //** isAuthorized
  //**************************************************************************
  /** Returns true if a user can access a given dashboard
   */
    private boolean isAuthorized(bluewave.app.User user, Dashboard dashboard,
        Database database, boolean readOnly) throws Exception {

        if (user.getAccessLevel()==5) return true;

        Connection conn = null;
        try{
            conn = database.getConnection();
            Recordset rs = new Recordset();

            String sql;
            if (readOnly){
                sql = "select read_only " +
                "from APPLICATION.DASHBOARD left join APPLICATION.DASHBOARD_USER " +
                "on APPLICATION.DASHBOARD.ID=APPLICATION.DASHBOARD_USER.dashboard_id " +
                "where dashboard.id=" + dashboard.getID() +
                " and (user_id=" + user.getID() + " or user_id is null)";
            }
            else{
                sql = "select read_only from APPLICATION.DASHBOARD_USER where " +
                "dashboard_id=" + dashboard.getID() + " and user_id=" + user.getID();
            }


            rs.open(sql, conn);
            boolean isAuthorized = false;
            if (!rs.EOF){
                isAuthorized = true;
                if (!readOnly){
                    Boolean isReadOnly = rs.getValue(0).toBoolean();
                    if (isReadOnly==null) isReadOnly = true;
                    if (isReadOnly) isAuthorized = false;
                }
            }
            rs.close();
            conn.close();
            return isAuthorized;
        }
        catch(Exception e){
            if (conn!=null) conn.close();
            throw e;
        }
    }


  //**************************************************************************
  //** SQLEditor
  //**************************************************************************
    private class SQLEditor {
        private javaxt.sql.Parser parser;
        private String sql;
        public SQLEditor(String sql){
            this.sql = sql;
        }
        public void append(String whereClause){
            if (parser==null) parser = new javaxt.sql.Parser(sql);
            String where = parser.getWhereString();
            if (where==null) where = "";
            else where += " and ";
            where += whereClause;
            parser.setWhere(where);
            sql = parser.toString();
        }
        public void remove(){
            if (parser==null) parser = new javaxt.sql.Parser(sql);
            parser.setWhere(null);
        }
        public String getSQL(){
            return sql;
        }
    }

}