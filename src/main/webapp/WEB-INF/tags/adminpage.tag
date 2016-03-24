<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:genericpage>
    <jsp:body>
    <div class="container">
        <h1>Coinblesk Admin</h1>
        <t:adminmenu />
        
        <div class="main">
              <jsp:doBody/>
        </div>
        
    </div>
    </jsp:body>
</t:genericpage>