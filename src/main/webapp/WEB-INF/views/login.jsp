<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:genericpage>
    <jsp:body>
	    <div class="container">
	   
	        <c:choose>
	            <c:when test="${param.logout != null}">
                    <div class="alert alert-success" role="alert">You have been logged out.</div>
                </c:when>
                
			    <c:when test="${param.success != null}">
			       <div class="alert alert-success" role="alert">You have successfully logged in.</div>
			    </c:when>

			    <c:otherwise>
			        <form class="form-signin" action="${pageContext.servletContext.contextPath}/login" method="post">
		               <h2 class="form-signin-heading">Please sign in</h2>
		               <c:if test="${param.error != null}"> 
		                    <div class="alert alert-danger" role="alert">Invalid username and password.</div>
		               </c:if>
		              
		               <label for="inputUsername" class="sr-only">Username</label>
		               <input type="text" id="inputUsername" name="username" class="form-control" placeholder="Email" required autofocus />
		               <label for="inputPassword" class="sr-only">Password</label>
		               <input type="password" id="inputPassword" name="password" class="form-control" placeholder="Password" required />
		               <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
		               <button class="btn btn-lg btn-primary btn-block" type="submit">Log in</button>
		            </form>
			    </c:otherwise>
		    </c:choose>
		    
	    </div><!-- /container -->            
	</jsp:body>
</t:genericpage>
