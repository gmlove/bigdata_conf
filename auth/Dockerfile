FROM centos:7

RUN yum install openssh-clients openssh-server -y
RUN echo 'root:screencast' | chpasswd && \
    sed -i 's/PermitRootLogin prohibit-password/PermitRootLogin yes/' /etc/ssh/sshd_config && \
    sed 's@session\s*required\s*pam_loginuid.so@session optional pam_loginuid.so@g' -i /etc/pam.d/sshd && \
    echo "export VISIBLE=now" >> /etc/profile && \
    ssh-keygen -t rsa -f /etc/ssh/ssh_host_rsa_key -P '' && ssh-keygen -t dsa -f /etc/ssh/ssh_host_dsa_key -P '' && \
    ssh-keygen -t rsa -P '' -f ~/.ssh/id_rsa && \
    cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys && \
    chmod 0600 ~/.ssh/authorized_keys

RUN yum install krb5-server krb5-libs krb5-workstation -y
ADD krb5.conf /etc/krb5.conf
ADD kdc.conf /var/kerberos/krb5kdc/kdc.conf
RUN echo -e '123456\n123456' | kdb5_util create -r HADOOP.COM -s


RUN mkdir /hd
RUN yum install wget vim less -y
RUN yum install -y java-1.8.0-openjdk-devel && \
    echo 'export JAVA_HOME=/usr/lib/jvm/java' >> ~/.bashrc

RUN yum install -y apache-commons-daemon-jsvc.x86_64


ADD files/hadoop-2.7.7.tar.gz /hd/
RUN ln -sv /hd/hadoop-2.7.7 /hd/hadoop
ADD hadoop/etc/hadoop/core-site.xml /hd/hadoop/etc/hadoop/
ADD hadoop/etc/hadoop/hdfs-site.xml /hd/hadoop/etc/hadoop/
ADD hadoop/etc/hadoop/mapred-site.xml /hd/hadoop/etc/hadoop/
ADD hadoop/etc/hadoop/yarn-site.xml /hd/hadoop/etc/hadoop/
ADD hadoop/etc/hadoop/hadoop-env.sh /hd/hadoop/etc/hadoop/
ADD hadoop/sbin/stop-dfs.sh /hd/hadoop/sbin/
ADD hadoop/sbin/start-dfs.sh /hd/hadoop/sbin


ARG build_host=shd


RUN cd /hd/hadoop && \
    sed -i "s/__HOST__/${build_host}/g" etc/hadoop/core-site.xml && \
    sed -i "s/__HOST__/${build_host}/g" etc/hadoop/hdfs-site.xml && \
    sed -i "s/__HOST__/${build_host}/g" etc/hadoop/mapred-site.xml && \
    sed -i "s/__HOST__/${build_host}/g" etc/hadoop/yarn-site.xml

RUN mkdir /hd/conf/ && \
    kadmin.local addprinc -randkey root/${build_host}@HADOOP.COM && \
    kadmin.local addprinc -randkey HTTP/${build_host}@HADOOP.COM && \
    kadmin.local xst -k /hd/conf/hadoop.keytab root/${build_host}@HADOOP.COM HTTP/${build_host}@HADOOP.COM && \
    kadmin.local addprinc -randkey root@HADOOP.COM && \
    kadmin.local xst -k /hd/conf/root.keytab root@HADOOP.COM

RUN echo "$(tail -n 1 /etc/hosts | cut -d' ' -f1) ${build_host} kdc-server kdc-server.hadoop.com" | tee -a /etc/hosts && \
    cat /etc/hosts && \
    /usr/sbin/krb5kdc && /usr/sbin/kadmind && sleep 1 && \
    cd /hd/hadoop && \
    echo ${build_host} > etc/hadoop/slaves && \
    bin/hdfs namenode -format

RUN yum install -y make net-tools


ADD files/apache-hive-1.2.2-bin.tar.gz /hd/
RUN cd /hd && ln -sv apache-hive-1.2.2-bin hive
RUN yum install -y mariadb-server
RUN /usr/libexec/mariadb-prepare-db-dir mariadb.service
RUN cd /hd && \
    (/usr/bin/mysqld_safe --basedir=/usr 2>&1 1>hive/hive.metastore.mysql.log &) && sleep 4 && \
    mysql -uroot -e "create user 'hive'@'localhost' identified by '123456'; grant all on *.* to hive@'localhost';"
ADD files/mysql-connector-java-5.1.48.jar /hd/hive/lib/
ADD hive/conf/hive-site.xml /hd/hive/conf/


ADD files/apache-zookeeper-3.5.5-bin.tar.gz /hd/
RUN cd /hd && ln -sv apache-zookeeper-3.5.5-bin zookeeper
RUN kadmin.local addprinc -randkey zookeeper/${build_host}@HADOOP.COM && \
    kadmin.local xst -k /hd/conf/zookeeper.keytab zookeeper/${build_host}@HADOOP.COM
ADD zookeeper/bin/zkEnv.sh /hd/zookeeper/bin/zkEnv.sh
ADD zookeeper/conf/jaas.conf /hd/zookeeper/conf/jaas.conf
ADD zookeeper/conf/client-jaas.conf /hd/zookeeper/conf/client-jaas.conf
ADD zookeeper/conf/zoo.cfg /hd/zookeeper/conf/zoo.cfg
RUN cd /hd/zookeeper && \
    sed -i "s/__HOST__/${build_host}/g" conf/jaas.conf && \
    sed -i "s/__HOST__/${build_host}/g" conf/client-jaas.conf


ADD files/hbase-1.3.6-bin.tar.gz /hd/
RUN cd /hd/ && ln -sv hbase-1.3.6 hbase
ADD hbase/conf/hbase-site.xml /hd/hbase/conf/hbase-site.xml
ADD hbase/conf/hbase-env.sh /hd/hbase/conf/hbase-env.sh
ADD hbase/conf/jaas.conf /hd/hbase/conf/jaas.conf
RUN cd /hd/ && echo ${build_host} > hbase/conf/regionservers
RUN cd /hd/hbase && \
    sed -i "s/__HOST__/${build_host}/g" conf/hbase-site.xml && \
    sed -i "s/__HOST__/${build_host}/g" conf/jaas.conf


ADD files/spark-2.1.0-bin-hadoop2.7.tgz /hd/
ADD files/mysql-connector-java-5.1.48.jar /hd/spark/jars/
RUN cd /hd && rm -r spark && ln -sv spark-2.1.0-bin-hadoop2.7 spark && \
    cp hive/conf/hive-site.xml spark/conf/

RUN yum install -y zip unzip
ADD files/livy-0.5.0-incubating-bin.zip /hd/
RUN cd /hd && unzip livy-0.5.0-incubating-bin.zip && rm livy-0.5.0-incubating-bin.zip && ln -sv livy-0.5.0-incubating-bin livy
ADD livy/conf/livy.conf /hd/livy/conf/
RUN cd /hd/livy && \
    sed -i "s/__HOST__/${build_host}/g" conf/livy.conf


ADD Makefile /hd/Makefile
ADD start-all.sh /hd/start-all.sh
RUN sed -i "s/__HOST__/${build_host}/g" /hd/start-all.sh && \
    chmod a+x /hd/start-all.sh


CMD /hd/start-all.sh
