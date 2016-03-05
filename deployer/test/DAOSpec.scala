import java.sql.SQLException

import com.toscaruntime.exception.deployment.execution.ConcurrentWorkflowExecutionException
import dao.DeploymentDAO
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.Application
import play.api.test.FakeApplication

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class DAOSpec extends PlaySpec with OneAppPerSuite with BeforeAndAfter with ScalaFutures {

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(2, Seconds), interval = Span(5, Millis))

  implicit override lazy val app: FakeApplication =
    FakeApplication(
      additionalConfiguration = Map(
        "slick.dbs.default.db.url" -> "jdbc:h2:mem:play",
        "com.toscaruntime.workspace" -> "./deployer/test/resources/testWorkspace"
      )
    )

  private val deploymentDAO = Application.instanceCache[DeploymentDAO].apply(app)

  before {
    Await.result(deploymentDAO.createSchema(), 5 seconds)
  }

  after {
    Await.result(deploymentDAO.dropSchema(), 5 seconds)
  }

  "DAO" must {
    "be able to insert and list nodes" in {
      whenReady(deploymentDAO.insertNodeIfNotExist("Compute", 1)) { result => result must be(1) }
      whenReady(deploymentDAO.listNodes()) { allNodes =>
        allNodes must have size 1
        allNodes.head.id must be("Compute")
        allNodes.head.instancesCount must be(1)
      }
    }
  }

  "DAO" must {
    "be able to insert and list instances" in {
      whenReady(deploymentDAO.insertNodeIfNotExist("Compute", 1)) { result => result must be(1) }
      whenReady(deploymentDAO.insertInstanceIfNotExist("Compute_1", "Compute", "initial")) { result => result must be(1) }
      whenReady(deploymentDAO.listInstances()) { allInstances =>
        allInstances must have size 1
        allInstances.head.id must be("Compute_1")
        allInstances.head.state must be("initial")
        allInstances.head.nodeId must be("Compute")
      }
    }
  }

  "DAO" must {
    "be able to insert and list attributes / outputs" in {
      whenReady(deploymentDAO.insertNodeIfNotExist("Compute", 1)) { result => result must be(1) }
      whenReady(deploymentDAO.insertInstanceIfNotExist("Compute_1", "Compute", "initial")) { result => result must be(1) }
      whenReady(deploymentDAO.saveInstanceAttribute("Compute_1", "ip_address", "0.0.0.0")) { result => result must be(1) }
      whenReady(deploymentDAO.saveInstanceAttribute("Compute_1", "public_ip_address", "0.0.0.1")) { result => result must be(1) }
      whenReady(deploymentDAO.getAttributes("Compute_1")) { allAttributes =>
        allAttributes must have size 2
        allAttributes must contain key "ip_address"
        allAttributes("ip_address") must be("0.0.0.0")
        allAttributes must contain key "public_ip_address"
        allAttributes("public_ip_address") must be("0.0.0.1")
      }
      whenReady(deploymentDAO.saveInstanceAttribute("Compute_1", "public_ip_address", "0.0.0.2")) { result => result must be(1) }
      whenReady(deploymentDAO.getAttributes("Compute_1")) { allAttributes =>
        allAttributes("public_ip_address") must be("0.0.0.2")
      }
      whenReady(deploymentDAO.saveInstanceAttribute("Compute_error", "public_ip_address", "0.0.0.2").failed) { result => result.isInstanceOf[SQLException] must be(true) }

      whenReady(deploymentDAO.saveOutput("Compute_1", "Standard", "create", "openstack_id", "great_id")) { result => result must be(1) }
      whenReady(deploymentDAO.getOutputs("Compute_1", "Standard", "create")) { allAttributes =>
        allAttributes must have size 1
        allAttributes must contain key "openstack_id"
        allAttributes("openstack_id") must be("great_id")
      }
      whenReady(deploymentDAO.saveOutput("Compute", "Standard", "create", "public_ip_address", "0.0.0.1").failed) { result => result.isInstanceOf[SQLException] must be(true) }
      whenReady(deploymentDAO.saveOutput("Compute_1", "Standard", "create", "openstack_id", "another_great_id")) { result => result must be(1) }
      whenReady(deploymentDAO.getOutputs("Compute_1", "Standard", "create")) { allAttributes =>
        allAttributes must have size 1
        allAttributes must contain key "openstack_id"
        allAttributes("openstack_id") must be("another_great_id")
      }
    }
  }

  "DAO" must {
    "be able to insert and list relationships" in {
      whenReady(deploymentDAO.insertNodeIfNotExist("Compute", 1)) { result => result must be(1) }
      whenReady(deploymentDAO.insertNodeIfNotExist("Software", 1)) { result => result must be(1) }
      whenReady(deploymentDAO.insertRelationshipIfNotExist("Software", "Compute", "HostedOn")) { result => result must be(1) }
      whenReady(deploymentDAO.listRelationships()) { relationships =>
        relationships must have size 1
        relationships.head.sourceId must be("Software")
        relationships.head.targetId must be("Compute")
        relationships.head.relationshipType must be("HostedOn")
      }
      whenReady(deploymentDAO.insertRelationshipIfNotExist("Software", "Compute_wrong", "HostedOn").failed) { result => result.isInstanceOf[SQLException] must be(true) }
      whenReady(deploymentDAO.insertRelationshipIfNotExist("Software_wrong", "Compute", "HostedOn").failed) { result => result.isInstanceOf[SQLException] must be(true) }
      whenReady(deploymentDAO.insertRelationshipIfNotExist("Software", "Compute", "HostedOn")) { result => result must be(0) }
      whenReady(deploymentDAO.insertRelationshipIfNotExist("Software", "Compute", "ConnectedTo")) { result => result must be(1) }
    }
  }

  "DAO" must {
    "be able to insert and list relationship instances" in {
      whenReady(deploymentDAO.insertNodeIfNotExist("Compute", 1)) { result => result must be(1) }
      whenReady(deploymentDAO.insertNodeIfNotExist("Software", 1)) { result => result must be(1) }
      whenReady(deploymentDAO.insertInstanceIfNotExist("Compute_1", "Compute", "initial")) { result => result must be(1) }
      whenReady(deploymentDAO.insertInstanceIfNotExist("Software_1", "Software", "initial")) { result => result must be(1) }
      whenReady(deploymentDAO.insertRelationshipIfNotExist("Software", "Compute", "HostedOn")) { result => result must be(1) }
      whenReady(deploymentDAO.insertRelationshipInstanceIfNotExist("Software_1", "Compute_1", "Software", "Compute", "HostedOn", "initial")) { result => result must be(1) }
      whenReady(deploymentDAO.listRelationshipInstances()) { relationshipInstances =>
        relationshipInstances must have size 1
        relationshipInstances.head.sourceInstanceId must be("Software_1")
        relationshipInstances.head.targetInstanceId must be("Compute_1")
        relationshipInstances.head.relationshipType must be("HostedOn")
        relationshipInstances.head.state must be("initial")
      }
      whenReady(deploymentDAO.insertRelationshipInstanceIfNotExist("Software_1", "Compute_wrong", "Software", "Compute", "HostedOn", "initial").failed) { result => result.isInstanceOf[SQLException] must be(true) }
      whenReady(deploymentDAO.insertRelationshipInstanceIfNotExist("Software_wrong", "Compute_1", "Software", "Compute", "HostedOn", "initial").failed) { result => result.isInstanceOf[SQLException] must be(true) }
      whenReady(deploymentDAO.insertRelationshipInstanceIfNotExist("Software_1", "Compute_1", "Software", "Compute", "HostedOn", "initial")) { result => result must be(0) }
      whenReady(deploymentDAO.insertRelationshipIfNotExist("Software", "Compute", "ConnectedTo")) { result => result must be(1) }
      whenReady(deploymentDAO.insertRelationshipInstanceIfNotExist("Software_1", "Compute_1", "Software", "Compute", "ConnectedTo", "initial")) { result => result must be(1) }
      whenReady(deploymentDAO.insertRelationshipIfNotExist("Software", "Compute", "DependsOn")) { result => result must be(1) }
      whenReady(deploymentDAO.insertRelationshipInstanceIfNotExist("Software_1", "Compute_1", "Software_wrong", "Compute", "DependsOn", "initial").failed) { result => result.isInstanceOf[SQLException] must be(true) }
      whenReady(deploymentDAO.insertRelationshipInstanceIfNotExist("Software_1", "Compute_1", "Software", "Compute_wrong", "DependsOn", "initial").failed) { result => result.isInstanceOf[SQLException] must be(true) }
    }
  }

  "DAO" must {
    "be able to insert and list relationship instances attributes / outputs" in {
      whenReady(deploymentDAO.insertNodeIfNotExist("Compute", 1)) { result => result must be(1) }
      whenReady(deploymentDAO.insertNodeIfNotExist("Software", 1)) { result => result must be(1) }
      whenReady(deploymentDAO.insertInstanceIfNotExist("Compute_1", "Compute", "initial")) { result => result must be(1) }
      whenReady(deploymentDAO.insertInstanceIfNotExist("Software_1", "Software", "initial")) { result => result must be(1) }
      whenReady(deploymentDAO.insertRelationshipIfNotExist("Software", "Compute", "HostedOn")) { result => result must be(1) }
      whenReady(deploymentDAO.insertRelationshipInstanceIfNotExist("Software_1", "Compute_1", "Software", "Compute", "HostedOn", "initial")) { result => result must be(1) }
      whenReady(deploymentDAO.saveRelationshipAttribute("Software_1", "Compute_1", "HostedOn", "install_dir", "/tmp")) { result => result must be(1) }
      whenReady(deploymentDAO.getRelationshipAttributes("Software_1", "Compute_1", "HostedOn")) { allAttributes =>
        allAttributes must have size 1
        allAttributes must contain key "install_dir"
        allAttributes("install_dir") must be("/tmp")
      }
      whenReady(deploymentDAO.saveRelationshipAttribute("Software_1", "Compute_1", "HostedOn", "install_dir", "/root")) { result => result must be(1) }
      whenReady(deploymentDAO.getRelationshipAttributes("Software_1", "Compute_1", "HostedOn")) { allAttributes =>
        allAttributes must have size 1
        allAttributes must contain key "install_dir"
        allAttributes("install_dir") must be("/root")
      }
      whenReady(deploymentDAO.saveRelationshipAttribute("Software_1", "Compute_error", "HostedOn", "install_dir", "/root").failed) { result => result.isInstanceOf[SQLException] must be(true) }
      whenReady(deploymentDAO.saveRelationshipAttribute("Software_error", "Compute_1", "HostedOn", "install_dir", "/root").failed) { result => result.isInstanceOf[SQLException] must be(true) }
      whenReady(deploymentDAO.saveAllRelationshipOutputs("Software_1", "Compute_1", "HostedOn", "Configure", "add_source", Map("install_dir" -> "/opt", "log_dir" -> "/var/log/test"))) { result => result must be(Some(2))}
      whenReady(deploymentDAO.getRelationshipOutputs("Software_1", "Compute_1", "HostedOn", "Configure", "add_source")) { allOutputs =>
        allOutputs must have size 2
        allOutputs must contain key "install_dir"
        allOutputs("install_dir") must be("/opt")
        allOutputs must contain key "log_dir"
        allOutputs("log_dir") must be("/var/log/test")
      }
      whenReady(deploymentDAO.saveAllRelationshipOutputs("Software_1", "Compute_1", "HostedOn", "Configure", "add_source", Map("url" -> "http://abc.com", "message" -> "I'm good"))) { result => result must be(Some(2))}
      whenReady(deploymentDAO.getRelationshipOutputs("Software_1", "Compute_1", "HostedOn", "Configure", "add_source")) { allOutputs =>
        allOutputs must have size 2
        allOutputs must contain key "url"
        allOutputs("url") must be("http://abc.com")
        allOutputs must contain key "message"
        allOutputs("message") must be("I'm good")
      }
    }
  }

  "DAO" must {
    "be able to insert and list executions" in {
      whenReady(deploymentDAO.startExecution("install", Map("a" -> "b", "c" -> 2))) { result =>
        result must not be empty
        result
      }
      whenReady(deploymentDAO.listExecutions()) { allExecutions =>
        allExecutions must have size 1
        allExecutions.head.endTime must be(empty)
        allExecutions.head.error must be(empty)
        allExecutions.head.workflowId must be("install")
        allExecutions.head.status must be("RUNNING")
        allExecutions.head.id must not be empty
        allExecutions.head.id
      }
      whenReady(deploymentDAO.startExecution("install").failed) { result => result.isInstanceOf[ConcurrentWorkflowExecutionException] must be(true) }
      whenReady(deploymentDAO.finishRunningExecution()) { result => result must be(1) }
      whenReady(deploymentDAO.listExecutions()) { allExecutions =>
        allExecutions must have size 1
        allExecutions.head.endTime must not be empty
        allExecutions.head.error must be(empty)
        allExecutions.head.workflowId must be("install")
        allExecutions.head.status must be("SUCCESS")
        allExecutions.head.id must not be empty
      }
      val anotherId = whenReady(deploymentDAO.startExecution("custom")) { result =>
        result must not be empty
        result
      }
      whenReady(deploymentDAO.cancelRunningExecution()) { result => result must be(1) }
      whenReady(deploymentDAO.getExecution(anotherId)) { allExecutions =>
        allExecutions must have size 1
        allExecutions.head.endTime must not be empty
        allExecutions.head.error must be(empty)
        allExecutions.head.workflowId must be("custom")
        allExecutions.head.status must be("CANCELED")
        allExecutions.head.id must not be empty
      }
    }
  }
}