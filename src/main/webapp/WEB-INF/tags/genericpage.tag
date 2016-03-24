<%@tag description="Page Template" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="sec" uri="http://www.springframework.org/security/tags"%>

<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <!-- The above 3 meta tags *must* come first in the head; any other head content must come *after* these tags -->
    <meta name="description" content="">
    <meta name="author" content="">
    <link rel="icon" href="${pageContext.servletContext.contextPath}/static/favicon.ico">

    <title>Coinblesk</title>

    <!-- Bootstrap core CSS -->
	<link href="${pageContext.servletContext.contextPath}/static/css/bootstrap.min.css" rel="stylesheet">
	<link href="${pageContext.servletContext.contextPath}/static/css/bootstrap-theme.min.css" rel="stylesheet">
	<link href="${pageContext.servletContext.contextPath}/static/css/coinblesk.css" rel="stylesheet">

  </head>

  <body>

	<nav class="navbar navbar-inverse navbar-fixed-top">
		<div class="container">
			<div class="navbar-header">
				<a class="navbar-brand" href="#">Coinblesk</a>
			</div>
			<div id="navbar" class="navbar-collapse collapse">
				<ul class="nav navbar-nav">
					<li><a href="${pageContext.servletContext.contextPath}/">Home</a></li>
					<sec:authorize access="isAuthenticated() and hasRole('ADMIN')">
					   <li><a href="${pageContext.servletContext.contextPath}/admin">Admin</a></li>
					</sec:authorize>
				</ul>
			
				<sec:authorize access="isAnonymous()">
					<div class="nav navbar-nav navbar-right" style="margin-right:0px;">
                        <a href="${pageContext.request.contextPath}/login" class="btn btn-primary navbar-btn" role="button">Login</a>
                    </div>
				</sec:authorize>
				<sec:authorize access="isAuthenticated()">
					<form class="navbar-form navbar-right" action="${pageContext.request.contextPath}/logout" method="post">
						<button type="submit" class="btn btn-primary">Logout</button>
					</form>
					
                    <p class="navbar-text navbar-right"><c:out value="${pageContext.request.userPrincipal.name}"/></p> 
				</sec:authorize>
				
			</div><!--/.nav-collapse -->
		</div>
	</nav>

    <div id="page">
        <jsp:doBody/>
    </div>


    <!-- Bootstrap core and other JavaScript files
    ================================================== -->
    <!-- Placed at the end of the document so the pages load faster -->
    <script src="${pageContext.servletContext.contextPath}/static/js/jquery-2.2.1.min.js"></script>
    <script src="${pageContext.servletContext.contextPath}/static/js/bootstrap.min.js"></script>
    <script src="${pageContext.servletContext.contextPath}/static/js/coinblesk.js"></script>
    
  </body>
</html>
