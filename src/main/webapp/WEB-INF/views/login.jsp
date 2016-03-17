<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:genericpage>
	<jsp:body>
		
		<form class="form-signin" action="login" method="post">
	      <h2 class="form-signin-heading">Please sign in</h2>
	      <c:if test="${param.error != null}"> 
            <div class="alert alert-danger" role="alert">Invalid username and password.</div>
	      </c:if>
	      <c:if test="${param.logout != null}">       
            <div class="alert alert-success" role="alert">You have been logged out.</div>
          </c:if>
	      
	      <label for="inputUsername" class="sr-only">Username</label>
	      <input type="text" id="inputUsername" name="username" class="form-control" placeholder="Username" required autofocus />
	      <label for="inputPassword" class="sr-only">Password</label>
	      <input type="password" id="inputPassword" name="password" class="form-control" placeholder="Password" required />
	      <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
	      <button class="btn btn-lg btn-primary btn-block" type="submit">Log in</button>
	    </form>

	</jsp:body>
</t:genericpage>
