/**
 * @license
 * Copyright 2017 The FOAM Authors. All Rights Reserved.
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package foam.dao;

import foam.core.FObject;
import foam.core.X;
import foam.mlang.order.Comparator;
import foam.mlang.predicate.Predicate;
import foam.nanos.pm.PM;

public class PMDAO
  extends ProxyDAO
{

  protected String putName_;
  protected String findName_;
  protected String removeName_;
  protected String removeAllName_;

  public PMDAO(X x, DAO delegate) {
    super(x, delegate);
    init();
  }

  void init() {
    putName_       = getOf().getId() + ":put";
    findName_      = getOf().getId() + ":find";
    removeName_    = getOf().getId() + ":remove";
    removeAllName_ = getOf().getId() + ":removeAll";
  }

  PM createPM(String name) {
    PM pm = new PM();
    // TODO: should be modelled
    pm.setClassType(PMDAO.getOwnClassInfo());
    pm.setName(name);
    pm.init_();
    return pm;
  }

  @Override
  public FObject put_(X x, FObject obj) {
    PM pm = createPM(putName_);
    try {
      return super.put_(x, obj);
    } finally {
      pm.log(x);
    }
  }

  @Override
  public FObject find_(X x, Object id) {
    PM pm = createPM(findName_);

    try {
      return super.find_(x, id);
    } finally {
      pm.log(x);
    }
  }

  @Override
  public FObject remove_(X x, FObject obj) {
    PM pm = createPM(removeName_);

    try {
      return super.remove_(x, obj);
    } finally {
      pm.log(x);
    }
  }

  @Override
  public void removeAll_(X x, long skip, long limit, Comparator order, Predicate predicate) {
    PM pm = createPM(removeAllName_);

    try {
      super.removeAll_(x, skip, limit, order, predicate);
    } finally {
      pm.log(x);
    }
  }
}
