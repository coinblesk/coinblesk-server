'use strict';

/**
* UserController
* @constructor
*/
var Header = function($scope, $location) {
        $scope.isActive = function (viewLocation) {
            return viewLocation === $location.path();
        };

};