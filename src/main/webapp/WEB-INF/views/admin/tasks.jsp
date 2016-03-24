<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:adminpage>
<jsp:body>
    <script>var current_admin_menu="#admin_menu_tasks";</script>
    <p>
        <button id="admin_task_remove_burned" type="button" class="btn btn-success">Remove burned</button>
    </p>
    <div class="panel panel-default">
        <div class="panel-heading">
            <h3 class="panel-title">Output</h3>
        </div>
       
        <div id="admin_task_output" class="panel-body pre-scrollable" style="font-family: 'Courier New', monospace; white-space: pre;">
        </div>
    </div>
    
</jsp:body>
</t:adminpage>