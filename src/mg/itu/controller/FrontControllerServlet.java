package mg.itu.controller;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import mg.itu.utils.Utils;

public class FrontControllerServlet extends HttpServlet {

        public List<String> listControllers = new ArrayList<>();

        @Override
        public void init() throws ServletException {
                String packageName = getServletContext().getInitParameter("package.controller");
                List<Class<?>> controllers = Utils.getControllers(packageName);
                listControllers = Utils.classesToString(controllers);      
        }

        private void processRequest(HttpServletRequest request, HttpServletResponse response)
                        throws ServletException, IOException {
                response.setContentType("text/html");
                PrintWriter out = response.getWriter();
                String url = request.getRequestURL().toString();
                out.println(url);

                for (String class1 : listControllers) {
                        out.println(class1);
                }
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                        throws ServletException, IOException {
                processRequest(request, response);
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                        throws ServletException, IOException {
                processRequest(request, response);
        }

}