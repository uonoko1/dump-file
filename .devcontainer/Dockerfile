FROM eclipse-temurin:17-jdk

# WSL2 と同じ UID/GID でユーザーを作成
ARG USERNAME=uonoko
ARG USER_UID=1000
ARG USER_GID=1000

# 既存のGID/UIDがある場合は削除してから作成
RUN (getent group $USER_GID && groupmod -n $USERNAME $(getent group $USER_GID | cut -d: -f1) || groupadd --gid $USER_GID $USERNAME) \
    && (id -u $USER_UID >/dev/null 2>&1 && userdel $(id -nu $USER_UID) || true) \
    && useradd --uid $USER_UID --gid $USER_GID -m $USERNAME \
    && apt-get update \
    && apt-get install -y sudo \
    && echo "$USERNAME ALL=(root) NOPASSWD:ALL" > /etc/sudoers.d/$USERNAME \
    && chmod 0440 /etc/sudoers.d/$USERNAME

# Node.js と sbt インストール
RUN apt-get update && apt-get install -y \
    curl \
    gnupg \
    apt-transport-https \
    && echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list \
    && curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | apt-key add \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get update \
    && apt-get install -y sbt nodejs \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Claude Code インストール
RUN npm install -g @anthropic-ai/claude-code

# ヒープダンプ解析用ツール（jmap, jps など）は JDK に含まれている

# Eclipse MAT (Memory Analyzer Tool) CLI版インストール
RUN apt-get update && apt-get install -y unzip wget \
    && wget -q https://ftp.jaist.ac.jp/pub/eclipse/mat/1.15.0/rcp/MemoryAnalyzer-1.15.0.20231206-linux.gtk.x86_64.zip \
       -O /tmp/mat.zip \
    && unzip -q /tmp/mat.zip -d /opt \
    && rm /tmp/mat.zip \
    && chmod +x /opt/mat/ParseHeapDump.sh \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# MAT を PATH に追加
ENV PATH="/opt/mat:${PATH}"

# ホストと同じパス構造を作成し、ユーザーに権限を付与
RUN mkdir -p /home/uonoko/Development/blog/dump-file \
    && chown -R $USERNAME:$USERNAME /home/$USERNAME

WORKDIR /home/uonoko/Development/blog/dump-file

# デフォルトユーザーを設定
USER $USERNAME
