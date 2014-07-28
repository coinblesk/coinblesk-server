'use strict';

var SpringAngularApp = SpringAngularApp || {};

var App = angular.module('SpringAngularApp', ['ngRoute', 'ui.bootstrap','ngSanitize', 'ngCookies', 'SpringAngularApp.filters', 'SpringAngularApp.services', 'SpringAngularApp.directives']);

var httpHeaders;

//Declare app level module which depends on filters, and services
App.config(['$routeProvider', '$httpProvider', '$provide', function($routeProvider, $httpProvider, $provide) {
    
	// ======= router configuration
    $routeProvider.when('/login', {
    	templateUrl: 'login',
    	controller: LoginController
    });
    $routeProvider.when('/home', {
        templateUrl: 'home',
        controller: HomeController
    });
    $routeProvider.when('/relation', {
        templateUrl: 'relation',
        controller: RelationController
    });
    $routeProvider.when('/users', {
        templateUrl: 'users',
        controller: UsersController
    });
    $routeProvider.when('/history', {
        templateUrl: 'history',
        controller: HistoryController
    });
    $routeProvider.otherwise({redirectTo: '/login'});

    // ======== http configuration
	
    // Intercept http calls.
    $provide.factory('authHttpInterceptor', function ($q, $rootScope, $location) {
	    return {
		    // On request success
		    request: function (config) {
		    	console.log(config); // Contains the data about the request before it is sent.
//		    	if (typeof config.data !== undefined){
//		            config.data.auth ='hasAuth';
//		            config.data.token = $cookies.token;
//		            config.data.idx = $cookies.idx;
//		            console.log(config);
//		        }
		    	
		    	if (angular.isDefined($rootScope.authToken)) {
      			var authToken = $rootScope.authToken;
      			if (SpringAngularAppConfig.useAuthTokenHeader) {
      				config.headers['X-Auth-Token'] = authToken;
      			} else {
      				config.url = config.url + "?token=" + authToken;
      			}
      		}
		    	// Return the config or wrap it in a promise if blank.
		    	return config || $q.when(config);
		    },
		    //On request failure
		    requestError: function(request){
              return $q.reject(request);
          },
		    // On response success
		    response: function (response) {
		    	if (response.status === 401) {
	                console.log("Response 401");
	                console.log(response);
	            }
		    	// Return the response or promise.
		    	return response || $q.when(response);
		    },
		    // On response failure
		    responseError: function (rejection) {
		    	var status = rejection.status;
      		var config = rejection.config;
      		var method = config.method;
      		var url = config.url;
      		var unauthorized = 401;
      		
		    	if (status === unauthorized) {
                  var deferred = $q.defer(),
                      req = {
                          config: config,
                          deferred: deferred
                      };
                  $rootScope.requests401.push(req);
                  $rootScope.$broadcast('event:loginRequired');
                  return deferred.promise;
              }else {
      			$rootScope.error = method + " on " + url + " failed with status " + status;
      		}
		    	// Return the promise rejection.
		    	return $q.reject(rejection);
		    }
	    };
  });
  
  httpHeaders = $httpProvider.defaults.headers;
  $httpProvider.interceptors.push('authHttpInterceptor');
    
}]);

App.run(['$rootScope', '$http', '$cookieStore', '$location', function($rootScope, $http, $cookieStore,  $location, userAccountFactory) {

    /**
     * Holds all the requests which failed due to 401 response.
     */
    $rootScope.requests401 = [];

    $rootScope.$on('event:loginRequired', function () {
    	$rootScope.requests401 = [];	    
		if ($location.path().indexOf("/login") == -1) {
			originalLocation = $location.path();
			$rootScope.error = "Please enter a valid username / password";
	    }
		$location.path('/login');;
    });
    
    /**
     * On 'event:loginConfirmed', resend all the 401 requests.
     */
    $rootScope.$on('event:loginConfirmed', function () {
        var i, requests = $rootScope.requests401,
            retry = function (req) {
                $http(req.config).then(function (response) {
                    req.deferred.resolve(response);
                });
            };

        for (i = 0; i < requests.length; i += 1) {
            retry(requests[i]);
        }
        $rootScope.requests401 = [];
    });
    
    /**
     * On 'event:loginRequest' send credentials to the server.
     */
	$rootScope.$on('event:loginRequest', function (event, credentials) {

		console.log("EVENT " + event);
		console.log("CRED " + credentials.username + " - " + credentials.password);
		$http({
			method: 'POST',
			url: 'j_spring_security_check',
			param: {j_username: credentials.username, j_password: credentials.password },
			headers: {'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'}
		})
		.success(function(data) {
			console.log(data);
			if(data ==='AUTHENTICATION_SUCCESS'){
				$rootScope.$broadcast('event:loginConfirmed');
			}
		})
		.error(function(data){
			console.log(data);
		});
	});
	
	/**
     * On 'logoutRequest' invoke logout on the server and broadcast 'event:loginRequired'.
     */
    $rootScope.$on('event:logoutRequest', function () {
    	$http.put('/j_spring_security_logout', {}).success(function() {
    		delete $rootScope.user;
    		delete $rootScope.authToken;
    		$cookieStore.remove('authToken');
      	});
    });
	
    var originalLocation = $location.path();
//	$location.path("/login");
	var authToken = $cookieStore.get('authToken');
	if (authToken !== undefined) {
		$rootScope.authToken = authToken;
		userAccountFactory.getUser(function(user) {
			$rootScope.user = user;
			$location.path(originalLocation);
		});
	}

}]);