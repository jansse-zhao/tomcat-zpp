/**
 * @className package-info.java
 * @author zpp
 * @version 1.0.0
 * @description TODO
 * @createTime 2024年06月24日 21:04:00
 *
 * <p>
 * Catalina：
 * Catalina是Tomcat的核心组件之一，它负责管理Tomcat的生命周期和处理请求。Catalina包括两个重要的组件，即Engine和Host。Engine负责管理多个虚拟主机，而Host则负责管理一个虚拟主机中的Web应用程序。在Tomcat中，Catalina是由多个Java类组成的。例如，Catalina类是Catalina组件的入口点，它调用其他组件来启动Tomcat服务器。另外，Engine类和Host类负责管理虚拟主机和Web应用程序。
 * <p>
 * Connector：
 * Connector是Tomcat中的另一个核心组件，它负责处理客户端和服务器之间的通信。Tomcat支持多种连接器，例如HTTP、HTTPS、AJP等。在Tomcat中，Connector由多个Java类组成，例如，Connector类是Connector组件的入口点，它负责处理客户端和服务器之间的通信。另外，Endpoint类和Acceptor类负责管理连接器。
 * <p>
 * Container：
 * Container是Tomcat中的另一个重要组件，它负责管理Web应用程序。在Tomcat中，Container由多个Java类组成，例如，Engine类和Host类负责管理虚拟主机和Web应用程序。另外，Context类负责管理Web应用程序的上下文。Tomcat中的容器是基于Java Servlet规范实现的。
 * <p>
 * Coyote：
 * Coyote是Tomcat中的另一个核心组件，它是Connector组件的核心实现。它负责处理客户端和服务器之间的低级协议，例如HTTP和HTTPS。Coyote是由多个Java类组成的，例如，AbstractProtocol类是Coyote的核心组件之一，它实现了处理连接请求和响应的功能。另外，Processor类负责处理HTTP请求。
 * <p>
 * Jasper：
 * Jasper是Tomcat中的另一个重要组件，它负责处理JSP页面。JSP是一种动态Web页面，它允许开发人员将Java代码嵌入到HTML页面中。在Tomcat中，Jasper由多个Java类组成，例如，JspServlet类负责处理JSP页面的请求。另外，JspRuntimeContext类负责管理JSP页面的运行时环境
 * <p>
 * <p>
 * <p>
 * <p>
 * 从一个完整请求的角度来看通过一个完整的HTTP请求，假设来自客户的请求为：http://localhost:8080/test/index.jsp
 * 请求被发送到本机端口8080，被在那里侦听的Coyote HTTP/1.1 Connector,
 * 1. 然后Connector把该请求交给它所在的Service的Engine来处理，并等待Engine的回应
 * 2. Engine获得请求localhost:8080/test/index.jsp，匹配它所有虚拟主机Host
 * 3. Engine匹配到名为localhost的Host(即使匹配不到也把请求交给该Host处理，因为该Host被定义为该Engine的默认主机)
 * 4. localhost Host获得请求/test/index.jsp，匹配它所拥有的所有Context
 * 5. Host匹配到路径为/test的Context(如果匹配不到就把该请求交给路径名为""的Context去处理)
 * 6. path="/test"的Context获得请求/index.jsp，在它的mapping table中寻找对应的servlet
 * 7. Context匹配到URL PATTERN为*.jsp的servlet，对应于JspServlet类，构造HttpServletRequest对象和HttpServletResponse对象，作为参数调用JspServlet的doGet或doPost方法
 * 8. Context把执行完了之后的HttpServletResponse对象返回给Host
 * 9. Host把HttpServletResponse对象返回给Engine
 * 10. Engine把HttpServletResponse对象返回给Connector
 * 11. Connector把HttpServletResponse对象返回给客户browser
 * <p>
 * <p>
 * <p>
 * 从功能的角度将Tomcat源代码分成5个子模块，分别是:
 * 一、Jsper模块: 这个子模块负责jsp页面的解析、jsp属性的验证，同时也负责将jsp页面动态转换为java代码并编译成class文件。
 * 在Tomcat源代码中，凡是属于org.apache.jasper包及其子包中的源代码都属于这个子模块;
 * 二、Servlet和Jsp模块: 这个子模块的源代码属于javax.servlet包及其子包，如我们非常熟悉的javax.servlet.Servlet接口、javax.servet.http.HttpServlet类及javax.servlet.jsp.HttpJspPage就位于这个子模块中;
 * 三、Catalina模块: 这个子模块包含了所有以org.apache.catalina开头的java源代码。该子模块的任务是规范了Tomcat的总体架构，定义了Server、Service、Host、Connector、Context、Session及Cluster等关键组件及这些组件的实现，
 * 这个子模块大量运用了Composite设计模式。同时也规范了Catalina的启动及停止等事件的执行流程。从代码阅读的角度看，这个子模块应该是我们阅读和学习的重点。
 * 四、Connector模块: 如果说上面三个子模块实现了Tomcat应用服务器的话，那么这个子模块就是Web服务器的实现。所谓连接器(Connector)就是一个连接客户和应用服务器的桥梁，
 * 它接收用户的请求，并把用户请求包装成标准的Http请求(包含协议名称，请求头Head，请求方法是Get还是Post等等)。同时，这个子模块还按照标准的Http协议，
 * 负责给客户端发送响应页面，比如在请求页面未发现时，connector就会给客户端浏览器发送标准的Http 404错误响应页面。
 * 五、Resource模块: 这个子模块包含一些资源文件，如Server.xml及Web.xml配置文件。严格说来，这个子模块不包含java源代码，但是它还是Tomcat编译运行所必需的。
 */
package org.apache;