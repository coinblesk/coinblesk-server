<%@tag description="Admin Menu" pageEncoding="UTF-8"%>
<!--  admin menu -->
<ul id="admin_menu" class="nav nav-tabs">
    <li id="admin_menu_overview" role="presentation"">
        <a href="${pageContext.servletContext.contextPath}/admin">Overview</a>
    </li>
    <li id="admin_menu_users" role="presentation">
        <a href="${pageContext.servletContext.contextPath}/admin/users">Users</a>
    </li>
    <li id="admin_menu_transactions" role="presentation">
        <a href="#">Transactions</a>
    </li>
    <li id="admin_menu_tasks" role="presentation">
        <a href="${pageContext.servletContext.contextPath}/admin/tasks">Tasks</a>
    </li>
</ul>
<!--  /admin menu -->