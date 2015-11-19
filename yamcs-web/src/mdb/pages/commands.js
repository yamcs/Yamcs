(function () {
    'use strict';

    angular
        .module('yamcs.mdb')
        .controller('MDBCommandsController',  MDBCommandsController);

    /* @ngInject */
    function MDBCommandsController($rootScope, mdbService, $routeParams) {
        var vm = this;

        var qname = '/' + $routeParams['ss'];

        vm.qname = qname;
        vm.title = qname;
        vm.mdbType = 'commands';

        $rootScope.pageTitle = 'Commands | Yamcs';

        mdbService.listCommands(qname).then(function (data) {
            vm.commands = data;
            return vm.commands;
        });
    }
})();
