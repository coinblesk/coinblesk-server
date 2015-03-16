'use strict';

var SpringAngularApp = SpringAngularApp || {};

var httpHeaders, message;

var App = angular.module('SpringAngularApp', 
		['ngRoute', 'ui.bootstrap','ngSanitize', 'ngCookies', 'SpringAngularApp.filters', 
		 'SpringAngularApp.services', 'SpringAngularApp.directives']);

//Declare app level module which depends on filters, and services
App.config(['$routeProvider', '$httpProvider', '$provide', function($routeProvider, $httpProvider, $provide) {
    
	// ======= router configuration
	$routeProvider.when('/login', {
    	templateUrl: 'login',
    	controller: LoginController
    });
    $routeProvider.when('/logout', {
    	templateUrl: '',
    	controller: LogoutController
    });
    $routeProvider.when('/home', {
        templateUrl: 'home',
        controller: HomeController
    });
    $routeProvider.when('/relation', {
        templateUrl: 'relation',
        controller: RelationController
    });
    $routeProvider.when('/serveraccount/:serverId', {
    	templateUrl: 'serveraccount',
    	controller: ServerAccountController
    });
    $routeProvider.when('/users', {
        templateUrl: 'users',
        controller: UsersController
    });
    $routeProvider.when('/history', {
        templateUrl: 'history',
        controller: HistoryController
    });
    $routeProvider.when('/activities', {
        templateUrl: 'activities',
        controller: ActivitiesController
    });
    $routeProvider.when('/messages', {
    	templateUrl: 'messages',
    	controller: MessagesController
    });
    $routeProvider.otherwise({redirectTo: '/login'});

    // ======== http configuration
	
    // Intercept http calls.
    $provide.factory('authHttpInterceptor', function ($q, $rootScope, $location) {
	    return {
		    // On request success
		    request: function (config) {
		    	
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
      		var accessdenied = 403;
      		
		    	if (status === unauthorized) {
                  var deferred = $q.defer(), req = {
                          config: config,
                          deferred: deferred
                      };
                  $rootScope.$broadcast('event:loginRequired');
                  return deferred.promise;
              } else if(status === accessdenied){
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
  
  $httpProvider.interceptors.push('authHttpInterceptor');
  //sets a resource in the header of each request
  $httpProvider.defaults.useXDomain = true;
  $httpProvider.defaults.withCredentials = true;
  delete $httpProvider.defaults.headers.common['X-Requested-With'];
}]);

App.run(function($rootScope, $http, $location, $cookieStore, $injector, base64Factory) {

    /**
     * Holds all the requests which failed due to 401 response.
     */
    $rootScope.requests401 = [];
    $rootScope.initialized = null;
    $rootScope.loggedusername = "";
    
    $rootScope.$on('event:loginRequired', function () {
    	if ($location.path().indexOf("/login") == -1) {
			$rootScope.error = "Please enter a valid username / password";
	    }
    	$rootScope.initialized = null;
		$location.path('/login');
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

		var getUrlrequest = $http({
			method: 'GET',
			headers: { 'Content-Type': 'application/json; charset=UTF-8'},
			url: 'login/url'
		});
		
		getUrlrequest.then(function(data){
			var username = credentials.username;
			credentials.username = username +"@"+ data.data.url;
			var payload = 'j_username=' + credentials.username + '&j_password=' + credentials.password;
			var config = {
					headers: {'Content-Type':'application/x-www-form-urlencoded; charset=UTF-8'}
			};
			var request = $http.post('j_spring_security_check', payload, config)
			.success(function(data) {
				$rootScope.$broadcast('event:loginConfirmed');
			})
			.error(function(data, status){
				console.log(status);
			});
			
			request.then(function(){
				$rootScope.loggedusername = credentials.username;
				$rootScope.initialized = true;
				$location.path('/home');
			});
		}, function(data, status){
			console.log(status);
		});
		
	});
	
	/**
     * On 'logoutRequest' invoke logout on the server and broadcast 'event:loginRequired'.
     */
    $rootScope.$on('event:logoutRequest', function (event) {
    	$rootScope.loggedusername = "";
    	$rootScope.initialized = null;
    	$http.post('/j_spring_security_logout', {})
    	.success(function() {
      	});
    	 $rootScope.$broadcast('event:loginRequired');
    });

});