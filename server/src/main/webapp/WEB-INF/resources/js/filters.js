'use strict';

/* Filters */

var AppFilters = angular.module('SpringAngularApp.filters', []);

AppFilters.filter('interpolate', ['version', function (version) {
    return function (text) {
        return String(text).replace(/\%VERSION\%/mg, version);
    };
}]);
AppFilters.filter('textOrNumber',['$filter', function ($filter) {
    return function (input, fractionSize) {
        if (isNaN(input)) {
            return input;
        } else {
            return $filter('number')(input, fractionSize);
        }
    };
}]);
