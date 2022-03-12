FROM gitpod/workspace-full

ADD https://raw.githubusercontent.com/lefou/millw/0.4.2/millw /usr/local/bin/mill
RUN sudo chmod +rx /usr/local/bin/mill
