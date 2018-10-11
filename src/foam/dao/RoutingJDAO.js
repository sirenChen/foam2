/**
 * @license
 * Copyright 2018 The FOAM Authors. All Rights Reserved.
 * http://www.apache.org/licenses/LICENSE-2.0
 */

foam.CLASS({
  package: 'foam.dao',
  name: 'RoutingJDAO',
  extends: 'foam.dao.java.JDAO',

  documentation:
    `JDAO that adds the service name to the context to use for routing to correct DAO.
    Doing this allows the underlying journal implementation to output the DAO name
    alongside the journal entry which will aid in using a single journal file.`,

  implements: [
    'foam.nanos.NanoService'
  ],

  axioms: [
    {
      name: 'javaExtras',
      buildJavaClass: function (cls) {
        cls.extras.push(`
          // TODO: These convenience constructors should be removed and done using the facade pattern.
          public RoutingJDAO(foam.core.X x, foam.core.ClassInfo classInfo, String service) {
            this(x, new foam.dao.MDAO(classInfo), service);
          }

          public RoutingJDAO(foam.core.X x, foam.dao.DAO delegate, String service) {
            setX(x);
            setOf(delegate.getOf());
            setDelegate(delegate);
            setService(service);
            setJournal((Journal) x.get("journal"));
          }
        `)
      }
    }
  ],

  properties: [
    {
      class: 'String',
      name: 'service',
      documentation: 'Name of the service'
    }
  ],

  methods: [
    {
      name: 'put_',
      javaCode: `
        return super.put_(x.put("service", getService()), obj);
      `
    },
    {
      name: 'remove_',
      javaCode: `
        return super.remove_(x.put("service", getService()), obj);
      `
    },
    {
      name: 'start',
      javaCode: `
        getJournal().replay(getX(), null);
      `
    }
  ]
});
