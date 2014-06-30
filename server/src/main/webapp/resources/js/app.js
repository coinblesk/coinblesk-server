'use strict';

var SpringAngularApp = SpringAngularApp || {};

var App = angular.module('SpringAngularApp', ['ngRoute', 'ui.bootstrap','ngSanitize', 'SpringAngularApp.filters', 'SpringAngularApp.services', 'SpringAngularApp.directives']);

// Declare app level module which depends on filters, and services
App.config(['$routeProvider', function ($routeProvider) {
//    $routeProvider.when('/login', {
//        templateUrl: 'login',
//        controller: LoginController
//    });

    $routeProvider.when('/home', {
        templateUrl: 'home',
        controller: HomeController
    });

//    $routeProvider.when('/', {
//        templateUrl: '',
//        controller: IndexController
//    });
//    
//    $routeProvider.when('/trustrelation', {
//        templateUrl: 'trustrelation',
//        controller: TrustRelationController
//    });
//    
//    $routeProvider.when('/users', {
//        templateUrl: 'users',
//        controller: UsersController
//    });
//    
//    $routeProvider.when('/history', {
//        templateUrl: 'history',
//        controller: HistoryController
//    });
    
    $routeProvider.otherwise({redirectTo: '/home'});
}]);

//App.run(function($rootScope, $location) {
//
//    // register listener to watch route changes
//    $rootScope.$on("$routeChangeStart", function(event, next, current) {
//
//        console.log("Routechanged sessionId="+$rootScope.SessionId);
//
//        if ($rootScope.SessionId == '' || $rootScope.SessionId == null) {
//
//            // no logged user, we should be going to #login
//            if (next.templateUrl == "login.html") {
//                // already going to #login, no redirect needed
//            } else {
//                // not going to #login, we should redirect now
//                $location.path("/login");
//            }
//        }
//    });
//});