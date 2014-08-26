<%@taglib uri="http://www.springframework.org/tags/form" prefix="form"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
	<title>MBPS Admin Registration</title>
</head>
<body>
<h2>MBPS Admin Registration</h2>
<form:form method="POST" action="setPassword">
<table>
<tr>
<td><form:label path="pw1">Username</form:label></td>
<td><form:input type="text" path="username" /></td>
</tr>
<tr>
<td><form:label path="pw1">Email</form:label></td>
<td><form:input type="email" path="email" /></td>
</tr>
<tr>
<td><form:label path="pw1">Password</form:label></td>
<td><form:input type="password" path="pw1" /></td>
</tr>
<tr>
<td><form:label path="pw2">Confirm Password</form:label></td>
<td><form:input type="password" path="pw2" /></td>
</tr>
<tr>
<td>
<% String token = (String) request.getAttribute("token");%>
<form:hidden path="token" value="${token}" />
</td>
</tr>
<tr>
<td colspan="2">
<input type="submit" value="Submit"/>
</td>
</tr>
</table>
</form:form>
</body>
</html>