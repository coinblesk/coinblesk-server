<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:adminpage>
<jsp:body>
    <script>var current_admin_menu="#admin_menu_overview";</script>
    <p>
        Info: <c:out value="${info}"/>
    </p>
    
</jsp:body>
</t:adminpage>