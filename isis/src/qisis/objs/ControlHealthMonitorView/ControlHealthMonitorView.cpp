/**
 * @file
 * $Date$
 * $Revision$
 *
 *   Unless noted otherwise, the portions of Isis written by the USGS are
 *   public domain. See individual third-party library and package descriptions
 *   for intellectual property information, user agreements, and related
 *   information.
 *
 *   Although Isis has been used by the USGS, no warranty, expressed or
 *   implied, is made by the USGS as to the accuracy and functioning of such
 *   software and related material nor shall the fact of distribution
 *   constitute any such warranty, and no responsibility is assumed by the
 *   USGS in connection therewith.
 *
 *   For additional information, launch
 *   $ISISROOT/doc//documents/Disclaimers/Disclaimers.html
 *   in a browser or see the Privacy &amp; Disclaimers page on the Isis website,
 *   http://isis.astrogeology.usgs.gov, and the USGS privacy and disclaimers on
 *   http://www.usgs.gov/privacy.html.
 */
#include "IsisDebug.h"

#include "ControlHealthMonitorView.h"

#include <QAction>
#include <QList>
#include <QSize>
#include <QSizePolicy>
#include <QToolBar>
#include <QVBoxLayout>
#include <QWidgetAction>

#include "ControlHealthMonitorWidget.h"
#include "ControlPointEditView.h"
#include "ControlPointEditWidget.h"
#include "CnetEditorView.h"

#include "ControlNet.h"
#include "ControlPoint.h"
#include "Directory.h"
#include "ToolPad.h"


namespace Isis {

  /**
   * Constructor.
   */
  ControlHealthMonitorView::ControlHealthMonitorView(Directory *directory, QWidget *parent) :
                        AbstractProjectItemView(parent) {
    m_directory = directory;
    ControlNet *net = m_directory->project()->activeControl()->controlNet();

    ControlNetVitals *vitals = new ControlNetVitals(net);
    m_controlHealthMonitorWidget = new ControlHealthMonitorWidget(vitals, parent);

    connect(m_controlHealthMonitorWidget, SIGNAL(openPointEditor(ControlPoint *)),
            this, SLOT(openPointEditor(ControlPoint *)));

    connect(m_controlHealthMonitorWidget, SIGNAL(openImageEditor()),
            this, SLOT(openImageEditor()));

    setCentralWidget(m_controlHealthMonitorWidget);

    m_permToolBar = new QToolBar("Standard Tools", 0);
    m_permToolBar->setObjectName("permToolBar");
    m_permToolBar->setIconSize(QSize(22, 22));

    m_activeToolBar = new QToolBar("Active Tool", 0);
    m_activeToolBar->setObjectName("activeToolBar");
    m_activeToolBar->setIconSize(QSize(22, 22));

    m_toolPad = new ToolPad("Tool Pad", 0);
    m_toolPad->setObjectName("toolPad");


//  m_ControlHealthMonitorWidget->addToPermanent(m_permToolBar);
//  m_ControlHealthMonitorWidget->addTo(m_activeToolBar);
//  m_ControlHealthMonitorWidget->addTo(m_toolPad);

    m_activeToolBarAction = new QWidgetAction(this);
    m_activeToolBarAction->setDefaultWidget(m_activeToolBar);

    setAcceptDrops(true);

    QSizePolicy policy = sizePolicy();
    policy.setHorizontalPolicy(QSizePolicy::Expanding);
    policy.setVerticalPolicy(QSizePolicy::Expanding);
    setSizePolicy(policy);

  }

  /**
   *  This SLOT is designed to intercept the openPointEditor() signal that's emitted
   *  Whenever a point is double clicked on inside of the ControlHealthMonitorWidget.
   *
   *  It is designed to open the ControlPointEditWidget and edit the point that was
   *  selected in the health monitor.
   *
   *  @param point The Control Point to be editted.
   */
  void ControlHealthMonitorView::openPointEditor(ControlPoint *point) {
    ControlPointEditWidget* widget = m_directory->addControlPointEditView()->controlPointEditWidget();

    if (point && point != widget->editPoint()) {
      widget->setEditPoint(point);
    }

    // m_directory->addCnetEditorView(m_directory->project()->activeControl());
  }


  /**
   *  This SLOT is designed to intercept the openImageEditor() signal that's emitted
   *  Whenever an image is double clicked on inside of the ControlHealthMonitorWidget.
   *
   *  It is designed to open the CubeDnView and populate it with the selected cubes.
   */
  void ControlHealthMonitorView::openImageEditor() {
    m_directory->addCubeDnView();
  }


  /**
   * Destructor
   */
  ControlHealthMonitorView::~ControlHealthMonitorView() {
    delete m_controlHealthMonitorWidget;
    delete m_permToolBar;
    delete m_activeToolBar;
    delete m_toolPad;

    m_permToolBar = 0;
    m_activeToolBar = 0;
    m_toolPad = 0;
  }


  /**
   * Returns the ControlHealthMonitorWidget.
   *
   * @return (ControlHealthMonitorWidget *) The currently active ControlHealthMonitorWidget.
   */
  ControlHealthMonitorWidget *ControlHealthMonitorView::controlHealthMonitorWidget() {
    return m_controlHealthMonitorWidget;
  }


  /**
   * Returns the suggested size for the widget.
   *
   * @return (QSize) The size
   */
  QSize ControlHealthMonitorView::sizeHint() const {
    return QSize(800, 600);
  }


  /**
   * Returns a list of actions for the permanent tool bar.
   *
   * @return (QList<QAction *>) The actions
   */
  QList<QAction *> ControlHealthMonitorView::permToolBarActions() {
    return m_permToolBar->actions();
  }


  /**
   * Returns a list of actions for the active tool bar.
   *
   * @return (QList<QAction *>) The actions
   */
  QList<QAction *> ControlHealthMonitorView::activeToolBarActions() {
    QList<QAction *> actions;
    actions.append(m_activeToolBarAction);
    return actions;
  }


  /**
   * Returns a list of actions for the tool pad.
   *
   * @return (QList<QAction *>) The actions
   */
  QList<QAction *> ControlHealthMonitorView::toolPadActions() {
    return m_toolPad->actions();
  }


}