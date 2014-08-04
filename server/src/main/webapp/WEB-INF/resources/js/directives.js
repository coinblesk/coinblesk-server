'use strict';

/* Directives */

var AppDirectives = angular.module('SpringAngularApp.directives', []);

AppDirectives.directive('appVersion', ['version', function (version) {
    return function (scope, elm, attrs) {
        elm.text(version);
    };
}]);

AppDirectives.directive('ngMatch',['$parse', function($parse) {
	var directive = {
			require:"?ngModel",
			restrict: 'A',
			link: link
	};
	
	return directive;
	
	function link(scope, elem, attrs, ctrl){
		//if ngModel is not defined do nothing
		if(!ctrl) return;
		if(!attrs["ngMatch"]) return;
		
		var firstPassword = $parse(attrs["ngMatch"]) ;
		
		var validator = function(value){
			//retrieve the first value of the input control
			var temp = firstPassword(scope),
			v = value === temp;
			ctrl.$setValidity('match', v);
			return value;
		};
		
		ctrl.$parsers.unshift(validator);
		ctrl.$formatters.push(validator);
		attrs.$observe("ngMatch", function(){
			validator(ctrl.$viewValue);
		});
	}
}]);